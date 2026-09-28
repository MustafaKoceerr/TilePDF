package com.mustafakoceerr.tilepdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mustafakoceerr.tilepdf.core.render.DraftPageImageSource
import com.mustafakoceerr.tilepdf.core.render.PageRenderer
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.ReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.text.TextSearchRepository
import com.mustafakoceerr.tilepdf.domain.OpenDocumentUseCase
import com.mustafakoceerr.tilepdf.domain.OpenedDocument
import com.mustafakoceerr.tilepdf.feature.reader.navigation.ReaderRoute
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

private const val SAVE_POSITION_DEBOUNCE_MS = 500L

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    openDocument: OpenDocumentUseCase,
    private val pageRenderer: PageRenderer,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val textSearchRepository: TextSearchRepository,
) : ViewModel() {

    private val uri = savedStateHandle.toRoute<ReaderRoute>().uri

    private val documentState = MutableStateFlow<DocumentState>(DocumentState.Loading)
    private val searchState = MutableStateFlow(SearchUiState())
    private val positionUpdates = MutableSharedFlow<ReadingPosition>(extraBufferCapacity = 1)
    private var searchJob: Job? = null

    val uiState: StateFlow<ReaderUiState> = combine(
        documentState,
        preferencesRepository.nightMode,
        searchState,
    ) { document, nightMode, search ->
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
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState.Loading)

    init {
        viewModelScope.launch {
            documentState.value = try {
                val opened = openDocument(uri)
                DocumentState.Opened(opened, DraftPageImageSource(opened.document, pageRenderer))
            } catch (_: FileNotFoundException) {
                DocumentState.Failed(ReaderError.NotFound)
            } catch (_: SecurityException) {
                DocumentState.Failed(ReaderError.PasswordProtected)
            } catch (_: IOException) {
                DocumentState.Failed(ReaderError.Corrupt)
            }
        }
        savePositionWhileReading()
    }

    fun onPositionChanged(position: ReadingPosition) {
        positionUpdates.tryEmit(position)
    }

    fun onToggleNightMode() {
        val current = (uiState.value as? ReaderUiState.Ready)?.nightMode ?: return
        viewModelScope.launch { preferencesRepository.setNightMode(!current) }
    }

    fun onSearch(query: String) {
        val opened = (documentState.value as? DocumentState.Opened) ?: return
        searchJob?.cancel()
        searchState.value = SearchUiState(query = query, isSearching = query.isNotBlank())
        searchJob = viewModelScope.launch {
            textSearchRepository.search(uri, opened.opened.document.pageCount, query)
                .onCompletion { searchState.update { it.copy(isSearching = false) } }
                .collect { match -> searchState.update { it.copy(matches = it.matches + match) } }
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
    }

    private sealed interface DocumentState {
        data object Loading : DocumentState
        data class Failed(val reason: ReaderError) : DocumentState
        data class Opened(
            val opened: OpenedDocument,
            val pageImages: DraftPageImageSource,
        ) : DocumentState
    }
}
