package com.mustafakoceerr.tilepdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mustafakoceerr.tilepdf.core.render.PageRenderer
import com.mustafakoceerr.tilepdf.core.render.TiledPageImageSource
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.outline.DocumentOutlineRepository
import com.mustafakoceerr.tilepdf.data.preferences.ReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.text.TextSearchRepository
import com.mustafakoceerr.tilepdf.di.ApplicationScope
import com.mustafakoceerr.tilepdf.domain.OpenDocumentUseCase
import com.mustafakoceerr.tilepdf.domain.OpenedDocument
import com.mustafakoceerr.tilepdf.feature.reader.navigation.ReaderRoute
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

private const val SAVE_POSITION_DEBOUNCE_MS = 500L
private const val SEARCH_DEBOUNCE_MS = 300L

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    openDocument: OpenDocumentUseCase,
    private val pageRenderer: PageRenderer,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val textSearchRepository: TextSearchRepository,
    private val outlineRepository: DocumentOutlineRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val uri = savedStateHandle.toRoute<ReaderRoute>().uri

    private val documentState = MutableStateFlow<DocumentState>(DocumentState.Loading)
    private val searchState = MutableStateFlow(SearchUiState())
    private val searchQuery = MutableStateFlow("")
    private val outline = MutableStateFlow<List<OutlineEntry>>(emptyList())
    private val positionUpdates = MutableSharedFlow<ReadingPosition>(extraBufferCapacity = 1)

    val uiState: StateFlow<ReaderUiState> = combine(
        documentState,
        preferencesRepository.nightMode,
        searchState,
        outline,
    ) { document, nightMode, search, outline ->
        when (document) {
            DocumentState.Loading -> ReaderUiState.Loading
            is DocumentState.Failed -> ReaderUiState.Error(document.reason)
            is DocumentState.Opened -> ReaderUiState.Ready(
                title = document.opened.displayName,
                pageSizes = document.opened.document.pageSizes,
                pageImages = document.pageImages,
                initialPosition = document.opened.position,
                nightMode = nightMode,
                search = search,
                outline = outline,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState.Loading)

    init {
        viewModelScope.launch {
            documentState.value = try {
                val opened = openDocument(uri)
                prepareSearch()
                loadOutline()
                DocumentState.Opened(
                    opened = opened,
                    pageImages = TiledPageImageSource(opened.document, pageRenderer, viewModelScope),
                )
            } catch (_: FileNotFoundException) {
                DocumentState.Failed(ReaderError.NotFound)
            } catch (_: SecurityException) {
                DocumentState.Failed(ReaderError.PasswordProtected)
            } catch (_: IOException) {
                DocumentState.Failed(ReaderError.Corrupt)
            }
        }
        savePositionWhileReading()
        searchAsQueryChanges()
    }

    fun onPositionChanged(position: ReadingPosition) {
        positionUpdates.tryEmit(position)
    }

    fun onToggleNightMode() {
        val current = (uiState.value as? ReaderUiState.Ready)?.nightMode ?: return
        viewModelScope.launch { preferencesRepository.setNightMode(!current) }
    }

    fun onOpenSearch() {
        searchState.update { it.copy(isActive = true) }
    }

    fun onCloseSearch() {
        searchState.value = SearchUiState()
        searchQuery.value = ""
    }

    fun onSearchQueryChange(query: String) {
        searchState.update { it.copy(query = query) }
        searchQuery.value = query
    }

    fun onNextMatch() = moveCurrentMatch(+1)

    fun onPreviousMatch() = moveCurrentMatch(-1)

    private fun moveCurrentMatch(step: Int) = searchState.update { state ->
        if (state.matches.isEmpty()) return@update state
        // Wraps around at either end, like find-in-page in a browser.
        state.copy(currentIndex = (state.currentIndex + step).mod(state.matches.size))
    }

    @OptIn(FlowPreview::class)
    private fun searchAsQueryChanges() = viewModelScope.launch {
        searchQuery
            .debounce(SEARCH_DEBOUNCE_MS)
            .distinctUntilChanged()
            // A new query cancels the search still running for the old one.
            .collectLatest { query ->
                searchState.update {
                    it.copy(matches = emptyList(), currentIndex = -1, isSearching = query.isNotBlank())
                }
                if (query.isBlank()) return@collectLatest
                val pageCount = documentState.filterIsInstance<DocumentState.Opened>()
                    .first().opened.document.pageCount
                textSearchRepository.search(uri, pageCount, query)
                    // An unreadable document simply has no matches.
                    .catch { }
                    .collect { pageMatches ->
                        searchState.update { state ->
                            state.copy(
                                matches = state.matches + pageMatches,
                                // Jump to the first match as soon as it's found.
                                currentIndex = state.currentIndex.coerceAtLeast(0),
                            )
                        }
                    }
                searchState.update { it.copy(isSearching = false) }
            }
    }

    private fun loadOutline() = viewModelScope.launch {
        // Optional extra: a PDF PdfBox can't parse simply shows no contents.
        try {
            outline.value = outlineRepository.outline(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun prepareSearch() = viewModelScope.launch {
        // Best effort: if text can't be read, searching just finds nothing.
        try {
            textSearchRepository.prepare(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    @OptIn(FlowPreview::class)
    private fun savePositionWhileReading() {
        viewModelScope.launch {
            positionUpdates
                .debounce(SAVE_POSITION_DEBOUNCE_MS)
                .collect { libraryRepository.saveReadingPosition(uri, it) }
        }
    }

    override fun onCleared() {
        (documentState.value as? DocumentState.Opened)?.opened?.document?.close()
        // viewModelScope is already cancelled here.
        applicationScope.launch { textSearchRepository.release(uri) }
    }

    private sealed interface DocumentState {
        data object Loading : DocumentState
        data class Failed(val reason: ReaderError) : DocumentState
        data class Opened(
            val opened: OpenedDocument,
            val pageImages: TiledPageImageSource,
        ) : DocumentState
    }
}
