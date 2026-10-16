package com.mustafakoceerr.tilepdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mustafakoceerr.tilepdf.core.common.runCatchingCancellable
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.render.PageRenderer
import com.mustafakoceerr.tilepdf.core.text.TextSelection
import com.mustafakoceerr.tilepdf.core.render.TiledPageImageSource
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.ReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.text.DocumentTextRepository
import com.mustafakoceerr.tilepdf.di.ApplicationScope
import com.mustafakoceerr.tilepdf.domain.OpenDocumentUseCase
import com.mustafakoceerr.tilepdf.domain.OutlineLocator
import com.mustafakoceerr.tilepdf.domain.OpenedDocument
import com.mustafakoceerr.tilepdf.feature.reader.navigation.ReaderRoute
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
    private val documentTextRepository: DocumentTextRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val uri = savedStateHandle.toRoute<ReaderRoute>().uri

    private val documentState = MutableStateFlow<DocumentState>(DocumentState.Loading)
    private val searchState = MutableStateFlow(SearchUiState())
    private val searchQuery = MutableStateFlow("")
    private val toc = MutableStateFlow(TocUiState())

    /**
     * Latest scroll position. A plain field, not state: it changes every
     * frame while scrolling, and only matters when the contents open.
     */
    private var position: ReadingPosition? = null
    private val selection = MutableStateFlow<Selection?>(null)
    private var selectionJob: Job? = null
    private val positionUpdates = MutableSharedFlow<ReadingPosition>(extraBufferCapacity = 1)

    val uiState: StateFlow<ReaderUiState> = combine(
        documentState,
        preferencesRepository.nightMode,
        searchState,
        toc,
        selection,
    ) { document, nightMode, search, toc, selection ->
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
                toc = toc,
                selection = selection?.toUiState(),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState.Loading)

    init {
        viewModelScope.launch {
            documentState.value = try {
                val opened = openDocument(uri)
                loadOutline(opened.document)
                DocumentState.Opened(
                    opened = opened,
                    pageImages = TiledPageImageSource(opened.document, pageRenderer, viewModelScope),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DocumentState.Failed(readerErrorFor(e) ?: throw e)
            }
        }
        savePositionWhileReading()
        searchAsQueryChanges()
    }

    fun onPositionChanged(position: ReadingPosition) {
        this.position = position
        positionUpdates.tryEmit(position)
    }

    fun onOpenToc() {
        val opened = (documentState.value as? DocumentState.Opened)?.opened ?: return
        val at = position ?: opened.position
        // The position is the fraction scrolled into the top page; convert to points.
        val pageHeight = opened.document.pageSizes.getOrNull(at.pageIndex)?.heightPt ?: 0
        toc.update { state ->
            state.copy(
                isOpen = true,
                currentIndex = OutlineLocator.currentIndex(
                    state.entries,
                    at.pageIndex,
                    at.pageOffsetFraction * pageHeight,
                ) ?: -1,
            )
        }
    }

    fun onCloseToc() {
        toc.update { it.copy(isOpen = false) }
    }

    fun onToggleNightMode() {
        viewModelScope.launch { preferencesRepository.toggleNightMode() }
    }

    /** Selects the word at a point on a page, given in top-left PDF points. */
    fun onLongPress(pageIndex: Int, x: Float, y: Float) {
        selectionJob?.cancel()
        val document = openedDocument() ?: return
        selectionJob = viewModelScope.launch {
            // Unreadable text: nothing to select.
            val page = runCatchingCancellable { documentTextRepository.pageText(document, pageIndex) }
                .getOrNull() ?: return@launch
            selection.value = TextSelection.wordAt(page, x, y)?.let { Selection(page, it) }
        }
    }

    /** Moves one end of the selection to the character nearest a point on its page. */
    fun onDragSelectionHandle(handle: SelectionHandle, x: Float, y: Float) {
        selection.update { current ->
            current ?: return@update null
            val index = TextSelection.charAt(current.page, x, y) ?: return@update current
            val (start, end) = when (handle) {
                SelectionHandle.Start -> index to current.range.last
                SelectionHandle.End -> current.range.first to index
            }
            // Dragging one handle past the other just flips which end it is.
            current.copy(range = minOf(start, end)..maxOf(start, end))
        }
    }

    fun onSelectAllOnPage() {
        selection.update { current ->
            current ?: return@update null
            TextSelection.all(current.page)?.let { current.copy(range = it) } ?: current
        }
    }

    fun onClearSelection() {
        selectionJob?.cancel()
        selection.value = null
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
                val document = documentState.filterIsInstance<DocumentState.Opened>()
                    .first().opened.document
                documentTextRepository.search(document, query)
                    // An unreadable or closed document simply has no more matches.
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

    private fun loadOutline(document: PdfDocument) = viewModelScope.launch {
        // Optional extra: a damaged outline simply shows no contents.
        runCatchingCancellable { document.outline() }
            .onSuccess { entries -> toc.update { it.copy(entries = entries) } }
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
        val document = openedDocument() ?: return
        document.close()
        // viewModelScope is already cancelled here.
        applicationScope.launch { documentTextRepository.release(document) }
    }

    private fun openedDocument() = (documentState.value as? DocumentState.Opened)?.opened?.document

    /** The page's text is kept so dragging handles needs no reload. */
    private data class Selection(val page: PageText, val range: IntRange) {
        fun toUiState(): SelectionUiState {
            // Separators have no box, so the ends use the nearest boxed chars.
            val first = range.first { page.box(it) != null }
            val last = range.last { page.box(it) != null }
            return SelectionUiState(
                pageIndex = page.pageIndex,
                text = TextSelection.text(page, range),
                rects = TextSelection.rects(page, range),
                startBox = page.box(first)!!,
                endBox = page.box(last)!!,
            )
        }
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
