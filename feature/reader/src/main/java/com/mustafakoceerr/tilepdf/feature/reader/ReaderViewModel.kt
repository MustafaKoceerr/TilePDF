package com.mustafakoceerr.tilepdf.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.mustafakoceerr.tilepdf.core.common.runCatchingCancellable
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.render.PageRenderer
import com.mustafakoceerr.tilepdf.core.text.TextSelection
import com.mustafakoceerr.tilepdf.core.render.ThumbnailSource
import com.mustafakoceerr.tilepdf.core.render.TiledPageImageSource
import com.mustafakoceerr.tilepdf.data.bookmark.BookmarkRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.ReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.text.DocumentTextRepository
import com.mustafakoceerr.tilepdf.di.ApplicationScope
import com.mustafakoceerr.tilepdf.domain.OpenDocumentUseCase
import com.mustafakoceerr.tilepdf.domain.OutlineLocator
import com.mustafakoceerr.tilepdf.domain.OpenedDocument
import com.mustafakoceerr.tilepdf.feature.reader.components.PageJump
import com.mustafakoceerr.tilepdf.feature.reader.navigation.ReaderRoute
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReadingMode
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
    private val openDocument: OpenDocumentUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val pageRenderer: PageRenderer,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val documentTextRepository: DocumentTextRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val uri = savedStateHandle.toRoute<ReaderRoute>().uri
    private var openJob: Job? = null

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
    private val linkTargets = MutableStateFlow(LinkTargets())
    private val currentPage = MutableStateFlow(0)
    private val fullScreen = MutableStateFlow(false)

    /** Where the page view starts: the saved position, or the latest one after a mode change. */
    private val startPosition = MutableStateFlow<ReadingPosition?>(null)
    private var selectionJob: Job? = null
    private val positionUpdates = MutableSharedFlow<ReadingPosition>(extraBufferCapacity = 1)

    val uiState: StateFlow<ReaderUiState> = combine(
        documentState,
        preferencesRepository.preferences,
        searchState,
        toc,
        combine(selection, linkTargets, currentPage, fullScreen, startPosition, ::PageState),
    ) { document, preferences, search, toc, pageState ->
        val (selection, links, page, fullScreen, start) = pageState
        when (document) {
            DocumentState.Loading -> ReaderUiState.Loading
            is DocumentState.Failed -> ReaderUiState.Error(document.reason)
            is DocumentState.NeedsPassword -> ReaderUiState.PasswordRequired(document.wrongPassword)
            is DocumentState.Opened -> ReaderUiState.Ready(
                title = document.opened.displayName,
                pageSizes = document.opened.document.pageSizes,
                pageImages = document.pageImages,
                thumbnails = document.thumbnails,
                initialPosition = start ?: document.opened.position,
                preferences = preferences,
                search = search,
                toc = toc,
                selection = selection?.toUiState(),
                jump = links.jump,
                externalLink = links.externalLink,
                currentPage = page,
                isCurrentPageBookmarked = toc.bookmarks.any { it.pageIndex == page },
                isFullScreen = fullScreen,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReaderUiState.Loading)

    init {
        open(password = null)
        savePositionWhileReading()
        searchAsQueryChanges()
    }

    /** Tries the document again with [password]; it is not kept afterwards. */
    fun onPasswordEntered(password: String) = open(password)

    private fun open(password: String?) {
        openJob?.cancel()
        documentState.value = DocumentState.Loading
        openJob = viewModelScope.launch {
            documentState.value = try {
                val opened = openDocument(uri, password)
                loadOutline(opened.document)
                currentPage.value = opened.position.pageIndex
                observeBookmarks(opened)
                DocumentState.Opened(
                    opened = opened,
                    pageImages = TiledPageImageSource(opened.document, pageRenderer, viewModelScope),
                    thumbnails = ThumbnailSource(opened.document, pageRenderer),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                when (val failure = openFailureFor(e) ?: throw e) {
                    is OpenFailure.NeedsPassword -> DocumentState.NeedsPassword(failure.wrongPassword)
                    is OpenFailure.Failed -> DocumentState.Failed(failure.reason)
                }
            }
        }
    }

    /**
     * A tap at top-left point ([x], [y]) on [pageIndex]: clears a selection
     * if there is one, otherwise follows a link there.
     */
    fun onTap(pageIndex: Int, x: Float, y: Float) {
        if (selection.value != null) {
            selection.value = null
            return
        }
        val document = openedDocument() ?: return
        viewModelScope.launch {
            val link = runCatchingCancellable { document.linkAt(pageIndex, x, y) }.getOrNull()
            when (val action = linkAction(link, document.pageCount)) {
                is LinkAction.Jump -> linkTargets.update { it.copy(jump = action.jump) }
                is LinkAction.Open -> linkTargets.update { it.copy(externalLink = action.uri) }
                LinkAction.Ignore -> Unit
                LinkAction.NoLink -> toggleFullScreen()
            }
        }
    }

    /** A tap between pages: clears a selection, otherwise toggles full screen. */
    fun onTapOutsidePage() {
        if (selection.value != null) {
            selection.value = null
        } else {
            toggleFullScreen()
        }
    }

    /** The search bar stays while searching, so the query can still be edited. */
    private fun toggleFullScreen() {
        if (!searchState.value.isActive) fullScreen.update { !it }
    }

    /** Bookmarks the page at the top of the screen, or removes its bookmark. */
    fun onToggleBookmark() {
        val opened = (documentState.value as? DocumentState.Opened)?.opened ?: return
        val page = currentPage.value
        viewModelScope.launch { bookmarkRepository.toggle(opened.uri, page) }
    }

    private fun observeBookmarks(opened: OpenedDocument) = viewModelScope.launch {
        bookmarkRepository.observe(opened.uri, opened.document.pageCount)
            .collect { bookmarks -> toc.update { it.copy(bookmarks = bookmarks) } }
    }

    fun onJumpHandled() = linkTargets.update { it.copy(jump = null) }

    fun onExternalLinkHandled() = linkTargets.update { it.copy(externalLink = null) }

    fun onPositionChanged(position: ReadingPosition) {
        this.position = position
        currentPage.value = position.pageIndex
        positionUpdates.tryEmit(position)
    }

    fun onOpenToc() {
        fullScreen.value = false
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

    fun onPageThemeSelected(theme: PageTheme) {
        viewModelScope.launch { preferencesRepository.setPageTheme(theme) }
    }

    fun onReadingModeSelected(mode: ReadingMode) {
        // The other page view starts where this one is now.
        (documentState.value as? DocumentState.Opened)?.opened?.let { opened ->
            startPosition.value = positionForModeSwitch(position ?: opened.position, opened.document.pageCount)
        }
        viewModelScope.launch { preferencesRepository.setReadingMode(mode) }
    }

    fun onKeepScreenOnChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setKeepScreenOn(enabled) }
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
        fullScreen.value = false
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
                .collect { position ->
                    // Saved under the URI it was opened from: possibly a private copy.
                    val opened = (documentState.value as? DocumentState.Opened)?.opened ?: return@collect
                    libraryRepository.saveReadingPosition(opened.uri, position)
                }
        }
    }

    override fun onCleared() {
        val document = openedDocument() ?: return
        document.close()
        // viewModelScope is already cancelled here.
        applicationScope.launch { documentTextRepository.release(document) }
    }

    private fun openedDocument() = (documentState.value as? DocumentState.Opened)?.opened?.document

    /** State of the page view that changes with taps and scrolling. */
    private data class PageState(
        val selection: Selection?,
        val links: LinkTargets,
        val currentPage: Int,
        val fullScreen: Boolean,
        val startPosition: ReadingPosition?,
    )

    /** Where a tapped link leads, until the screen has acted on it. */
    private data class LinkTargets(val jump: PageJump? = null, val externalLink: String? = null)

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
        data class NeedsPassword(val wrongPassword: Boolean) : DocumentState
        data class Opened(
            val opened: OpenedDocument,
            val pageImages: TiledPageImageSource,
            val thumbnails: ThumbnailSource,
        ) : DocumentState
    }
}
