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
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.fill
import com.mustafakoceerr.tilepdf.model.DocumentView
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind
import com.mustafakoceerr.tilepdf.data.document.SharedDocuments
import com.mustafakoceerr.tilepdf.data.signature.SignatureStore
import com.mustafakoceerr.tilepdf.data.thumbnail.ThumbnailStore
import com.mustafakoceerr.tilepdf.model.Signature
import com.mustafakoceerr.tilepdf.model.normalizeSignature
import com.mustafakoceerr.tilepdf.model.placedAround
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationRepository
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaveScheduler
import com.mustafakoceerr.tilepdf.data.annotation.SaveResult
import com.mustafakoceerr.tilepdf.data.annotation.SessionPasswords
import com.mustafakoceerr.tilepdf.data.bookmark.BookmarkRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.ReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.text.DocumentTextRepository
import com.mustafakoceerr.tilepdf.di.ApplicationScope
import com.mustafakoceerr.tilepdf.domain.OpenDocumentUseCase
import com.mustafakoceerr.tilepdf.domain.SaveAsCopyUseCase
import com.mustafakoceerr.tilepdf.domain.OutlineLocator
import com.mustafakoceerr.tilepdf.domain.OpenedDocument
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationController
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationEditing
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTarget
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTool
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationsUiState
import com.mustafakoceerr.tilepdf.feature.reader.annotation.Erased
import com.mustafakoceerr.tilepdf.feature.reader.annotation.NoteEditor
import com.mustafakoceerr.tilepdf.feature.reader.annotation.annotationAt
import com.mustafakoceerr.tilepdf.feature.reader.annotation.markupFor
import com.mustafakoceerr.tilepdf.feature.reader.components.PageJump
import com.mustafakoceerr.tilepdf.feature.reader.navigation.ReaderRoute
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReadingMode
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.model.newAnnotationId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val SAVE_POSITION_DEBOUNCE_MS = 500L
private const val SEARCH_DEBOUNCE_MS = 300L

/** A signature as wide as one written by hand on a form line. */
private const val SIGNATURE_WIDTH_PT = 160f
private const val SIGNATURE_PEN_PT = 1.5f
private const val SIGNATURE_COLOR = 0xFF000000.toInt()

/** Twice the library's 40 dp row picture at most densities, still small as a PNG. */
private const val THUMBNAIL_WIDTH_PX = 240

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val openDocument: OpenDocumentUseCase,
    private val bookmarkRepository: BookmarkRepository,
    private val pageRenderer: PageRenderer,
    private val libraryRepository: LibraryRepository,
    private val preferencesRepository: ReaderPreferencesRepository,
    private val documentTextRepository: DocumentTextRepository,
    private val annotationRepository: AnnotationRepository,
    private val saveScheduler: AnnotationSaveScheduler,
    private val sessionPasswords: SessionPasswords,
    private val saveAsCopy: SaveAsCopyUseCase,
    private val sharedDocuments: SharedDocuments,
    private val thumbnailStore: ThumbnailStore,
    private val signatureStore: SignatureStore,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val uri = savedStateHandle.toRoute<ReaderRoute>().uri

    /**
     * What to open: the route's URI at first, then the URI it was opened as,
     * possibly a private copy. Reopening a copy must not copy its source again.
     */
    private var documentUri = uri

    /**
     * A document replaced by a reopen. Closed only once its successor is open,
     * when its pages have long left the screen: a render still running on it
     * must not find it closed.
     */
    private var retiring: OpenedDocument? = null

    /**
     * Each page's content box for cropping margins, found once per document;
     * the pages don't change when a save reopens it.
     */
    private var margins: List<PdfRect?>? = null
    private var marginsJob: Job? = null

    /** Whether the open document has a form to fill; read once per open. */
    private var documentHasForm = false

    /** Fillable widgets by page, loaded around the reader as it moves. */
    private val formFields = MutableStateFlow<Map<Int, List<FormField>>>(emptyMap())

    /** A text or choice field being filled in through a dialog. */
    private val formEditor = MutableStateFlow<FormField?>(null)

    private val signatureStep = MutableStateFlow<SignatureStep?>(null)

    /** Reopens the document when a save wrote a new version; one per controller. */
    private var fileSavedJob: Job? = null

    /** The outcome of "Kopya olarak kaydet", until the screen has acted on it. */
    private val saveCopyResult = MutableStateFlow<SaveCopyResult?>(null)
    private val handOff = MutableStateFlow<HandOff?>(null)
    private var handOffJob: Job? = null
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

    /**
     * Where the last jump started. Kept across a reopen after a save: the
     * pages are the same, and the reader is still where the jump took them.
     */
    private val jumpHistory = MutableStateFlow(JumpHistory())
    private val currentPage = MutableStateFlow(0)
    private val fullScreen = MutableStateFlow(false)

    /** Where the page view starts: the saved position, or the latest one after a mode change. */
    private val startPosition = MutableStateFlow<ReadingPosition?>(null)
    private var selectionJob: Job? = null
    private val positionUpdates = MutableSharedFlow<ReadingPosition>(extraBufferCapacity = 1)

    /** The open document's annotations; replaced when the document is (re)opened. */
    private val controller = MutableStateFlow<AnnotationController?>(null)
    private val editing = MutableStateFlow(AnnotationEditing())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val annotations = controller.flatMapLatest { it?.state ?: flowOf(AnnotationsUiState()) }

    val uiState: StateFlow<ReaderUiState> = combine(
        documentState,
        preferencesRepository.preferences,
        combine(searchState, toc, ::Pair),
        combine(selection, linkTargets, currentPage, fullScreen, startPosition, ::PageState),
        combine(annotations, editing, combine(saveCopyResult, handOff, ::Pair), jumpHistory, combine(formFields, formEditor, signatureStep, ::Triple), ::Outputs),
    ) { document, preferences, searchAndToc, pageState, annotationUi ->
        val (search, toc) = searchAndToc
        val (selection, links, page, fullScreen, start) = pageState
        val (annotations, editing, results, history, forms) = annotationUi
        val (copyResult, handOff) = results
        when (document) {
            DocumentState.Loading -> ReaderUiState.Loading
            is DocumentState.Failed -> ReaderUiState.Error(document.reason)
            is DocumentState.NeedsPassword -> ReaderUiState.PasswordRequired(document.wrongPassword)
            is DocumentState.Opened -> ReaderUiState.Ready(
                title = document.opened.displayName,
                pageSizes = document.transforms.map { it.size },
                pageTransforms = document.transforms,
                documentView = document.view,
                pageImages = document.pageImages,
                thumbnails = document.thumbnails,
                initialPosition = start ?: document.opened.position,
                preferences = preferences,
                search = search,
                toc = toc,
                selection = selection?.toUiState(),
                jump = links.jump,
                backToPage = history.origin?.pageIndex,
                externalLink = links.externalLink,
                currentPage = page,
                isCurrentPageBookmarked = toc.bookmarks.any { it.pageIndex == page },
                isFullScreen = fullScreen,
                annotations = annotations,
                editing = editing,
                saveCopyResult = copyResult,
                handOff = handOff,
                formFields = forms.first,
                formEditor = forms.second,
                signatureStep = forms.third,
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

    /** After running out of memory: other apps may have freed some since. */
    fun onRetryOpen() = open(password = null)

    private fun open(password: String?) {
        openJob?.cancel()
        documentState.value = DocumentState.Loading
        openJob = viewModelScope.launch {
            documentState.value = try {
                val opened = try {
                    openDocument(documentUri, password)
                } finally {
                    retireReplacedDocument()
                }
                documentUri = opened.uri
                // Kept in memory so a background save can reopen the file.
                if (password != null) sessionPasswords.remember(opened.uri, password)
                loadOutline(opened.document)
                currentPage.value = opened.position.pageIndex
                observeBookmarks(opened)
                // A password-protected document's first page isn't kept unencrypted.
                if (password == null) saveThumbnail(opened)
                documentHasForm = runCatchingCancellable { opened.document.hasForm() }.getOrDefault(false)
                // Values filled in but not saved yet show as filled; a failed one shows as the file has it.
                if (documentHasForm) {
                    val pending = annotationRepository.observe(opened.uri).first()?.formValues.orEmpty()
                    runCatchingCancellable { opened.document.fill(pending) }
                }
                formFields.value = emptyMap()
                val view = libraryRepository.documentView(opened.uri)
                if (margins?.size != opened.document.pageCount) margins = null
                if (view.cropMargins) findMargins(opened)
                shown(opened, view).also(::startAnnotations)
            } catch (e: Throwable) {
                // Throwable, not Exception: running out of memory is an Error.
                if (e is CancellationException) throw e
                when (val failure = openFailureFor(e) ?: throw e) {
                    is OpenFailure.NeedsPassword -> DocumentState.NeedsPassword(failure.wrongPassword)
                    is OpenFailure.Failed -> DocumentState.Failed(failure.reason)
                }
            }
            loadFormFields(currentPage.value)
        }
    }

    /** Loads the form fields of [around] and its neighbours, the pages a tap can reach. */
    private fun loadFormFields(around: Int) {
        if (!documentHasForm) return
        val document = openedDocument() ?: return
        val pages = (around - 1..around + 1).filter { it in 0 until document.pageCount && it !in formFields.value }
        if (pages.isEmpty()) return
        viewModelScope.launch {
            val loaded = pages.associateWith { page -> fieldsOf(document, page) }
            formFields.update { it + loaded }
        }
    }

    /** [pageIndex]'s fields, read from the engine; none when it can't tell. */
    private suspend fun fieldsOf(document: PdfDocument, pageIndex: Int): List<FormField> =
        runCatchingCancellable { document.formFields(pageIndex) }.getOrDefault(emptyList())

    /** A check box or radio button changes at once; text and choices open a dialog. */
    private fun onFormFieldTapped(field: FormField) {
        val value = tapValue(field)
        when {
            value != null -> setFormValue(field.name, value)
            field.kind == FormFieldKind.Text || field.kind == FormFieldKind.ComboBox || field.kind == FormFieldKind.ListBox ->
                formEditor.value = field
        }
    }

    fun onFormValueSubmitted(value: String) {
        val field = formEditor.value ?: return
        formEditor.value = null
        if (value != field.value) setFormValue(field.name, value)
    }

    fun onFormEditorDismissed() {
        formEditor.value = null
    }

    /**
     * Fills field [name] with [value]: kept in the draft until saved, and put
     * into the open document so its pages show it at once.
     */
    private fun setFormValue(name: String, value: String) {
        val state = documentState.value as? DocumentState.Opened ?: return
        val opened = state.opened
        viewModelScope.launch {
            annotationRepository.setFormValue(opened.uri, name, value)
            val pages = runCatchingCancellable { opened.document.fill(mapOf(name to value)) }.getOrDefault(emptySet())
            stateOf(opened)?.let { shown ->
                pages.forEach { page ->
                    shown.pageImages.invalidate(page)
                    shown.thumbnails.invalidate(page)
                }
            }
            val reloaded = pages.associateWith { page -> fieldsOf(opened.document, page) }
            formFields.update { it + reloaded }
            // The field may keep less than it was given (a length limit, say): the draft
            // holds what the file will hold, or every save would fail to verify.
            val kept = reloaded.values.flatten().firstOrNull { it.name == name }?.value
            if (kept != null && kept != value) annotationRepository.setFormValue(opened.uri, name, kept)
        }
    }

    /** The library's picture of the first page; redrawn on every open, so it follows changes to the file. */
    private fun saveThumbnail(opened: OpenedDocument) = viewModelScope.launch {
        // The document may close while this runs; the library then keeps the old picture.
        runCatchingCancellable {
            thumbnailStore.save(opened.uri, pageRenderer.renderPagePng(opened.document, 0, THUMBNAIL_WIDTH_PX))
        }
    }

    /** [opened] as [view] shows it, with image sources that draw it so. */
    private fun shown(opened: OpenedDocument, view: DocumentView): DocumentState.Opened {
        val transforms = pageTransforms(opened.document.pageSizes, view, margins)
        return DocumentState.Opened(
            opened = opened,
            pageImages = TiledPageImageSource(opened.document, pageRenderer, viewModelScope, transforms = transforms),
            thumbnails = ThumbnailSource(opened.document, pageRenderer, transforms = transforms),
            view = view,
            transforms = transforms,
        )
    }

    /** The open document's state, if it's still [opened]: views and reopens replace the state, not the document. */
    private fun stateOf(opened: OpenedDocument) =
        (documentState.value as? DocumentState.Opened)?.takeIf { it.opened === opened }

    /** Shows the open document as [view]; new transforms need new image sources. */
    private fun show(view: DocumentView) {
        val current = documentState.value as? DocumentState.Opened ?: return
        val next = shown(current.opened, view)
        if (next.transforms == current.transforms && next.view == current.view) return
        // Pending renders of the old view stop here.
        current.pageImages.requestTiles(emptyList())
        documentState.value = next
    }

    /** Finds every page's content box in the background, then shows the cropped pages. */
    private fun findMargins(opened: OpenedDocument) {
        if (margins != null || marginsJob?.isActive == true) return
        marginsJob = viewModelScope.launch {
            val found = (0 until opened.document.pageCount).map { page ->
                runCatchingCancellable { pageRenderer.marginCrop(opened.document, page) }.getOrNull()
            }
            // A reopen closed this document meanwhile: its boxes may be missing pages.
            val state = stateOf(opened) ?: return@launch
            // Read last: by now the draft has long loaded. Later notes go where the cropped page shows.
            margins = cropsWithAnnotations(found, controller.value?.state?.value?.own.orEmpty())
            show(state.view)
        }
    }

    /** Turns every page a quarter turn clockwise; kept for this document. */
    fun onRotate() {
        val state = documentState.value as? DocumentState.Opened ?: return
        val view = state.view.copy(rotation = nextRotation(state.view.rotation))
        show(view)
        viewModelScope.launch { libraryRepository.setRotation(state.opened.uri, view.rotation) }
    }

    /** Shows pages without their empty margins, once they're found; kept for this document. */
    fun onCropMarginsChange(enabled: Boolean) {
        val state = documentState.value as? DocumentState.Opened ?: return
        if (enabled) findMargins(state.opened)
        show(state.view.copy(cropMargins = enabled))
        viewModelScope.launch { libraryRepository.setCropMargins(state.opened.uri, enabled) }
    }

    private fun startAnnotations(state: DocumentState.Opened) {
        controller.value?.close()
        val opened = state.opened
        val created = AnnotationController(opened.uri, annotationRepository, saveScheduler, applicationScope) { ref, hidden ->
            // The document may close while this runs; then there's nothing left to redraw.
            runCatchingCancellable {
                opened.document.setForeignHidden(ref.pageIndex, ref.key, hidden)
                withContext(Dispatchers.Main) {
                    // The sources of the view shown now, which may not be the first.
                    stateOf(opened)?.let {
                        it.pageImages.invalidate(ref.pageIndex)
                        it.thumbnails.invalidate(ref.pageIndex)
                    }
                }
            }
        }
        controller.value = created
        viewModelScope.launch {
            // Unreadable annotations leave editing off rather than the document closed.
            runCatchingCancellable { created.prepare(opened.document) }
        }
        fileSavedJob?.cancel()
        fileSavedJob = viewModelScope.launch {
            created.fileSaved.collect { stateOf(opened)?.let(::reopenAfterSave) }
        }
    }

    private fun retireReplacedDocument() {
        val old = retiring ?: return
        retiring = null
        old.document.close()
        applicationScope.launch { documentTextRepository.release(old.document) }
    }

    /**
     * A save wrote a new version of the file, so the open document is the old
     * one: reopen at the same place. Positions of other apps' annotations may
     * have shifted, and the undo history ends here.
     */
    private fun reopenAfterSave(old: DocumentState.Opened) {
        if (documentState.value !== old) return
        startPosition.value = position ?: old.opened.position
        // Pending tile renders of the old version stop here.
        old.pageImages.requestTiles(emptyList())
        retiring = old.opened
        open(sessionPasswords.get(old.opened.uri))
    }

    /** The screen went to the background: save what's unsaved. */
    fun onStop() {
        controller.value?.requestSave()
    }

    /** Writes the annotated document into [target], a file the user picked, and opens it. */
    fun onSaveCopy(target: String) {
        val opened = (documentState.value as? DocumentState.Opened)?.opened ?: return
        val controller = controller.value ?: return
        viewModelScope.launch {
            controller.flush()
            val result = saveAsCopy(opened.uri, target, opened.displayName, opened.document.pageCount)
            saveCopyResult.value = if (result is SaveResult.Saved) SaveCopyResult.Saved(result.uri) else SaveCopyResult.Failed
        }
    }

    fun onSaveCopyResultHandled() {
        saveCopyResult.value = null
    }

    /** Exports the annotated document for the share sheet. */
    fun onShare() = handOff { uri, opened -> HandOff.Share(uri, opened.displayName) }

    /** Exports the annotated document for the system print dialog. */
    fun onPrint() = handOff { uri, opened -> HandOff.Print(uri, opened.displayName, opened.document.pageCount) }

    fun onHandOffHandled() {
        handOff.value = null
    }

    private fun handOff(make: (uri: String, OpenedDocument) -> HandOff) {
        val opened = (documentState.value as? DocumentState.Opened)?.opened ?: return
        // A second tap while exporting would rewrite the file being exported.
        if (handOffJob?.isActive == true) return
        handOffJob = viewModelScope.launch {
            controller.value?.flush()
            val shared = sharedDocuments.export(opened.uri, opened.displayName)
            handOff.value = shared?.let { make(it, opened) } ?: HandOff.Failed
        }
    }

    /** Tries the background save again after it failed. */
    fun onRetrySave() {
        controller.value?.requestSave()
    }

    /**
     * A tap at top-left point ([x], [y]) on [pageIndex]: clears a selection,
     * places a note, opens a tapped annotation's menu, follows a link, or
     * toggles full screen, in that order. Pen and eraser handle their own taps.
     */
    fun onTap(pageIndex: Int, x: Float, y: Float) {
        (signatureStep.value as? SignatureStep.Place)?.let { step ->
            placeSignature(step.signature, pageIndex, PdfPoint(x, y))
            return
        }
        if (selection.value != null) {
            selection.value = null
            return
        }
        val tools = editing.value
        if (tools.drawing) return
        val annotations = controller.value?.state?.value
        if (tools.annotating && tools.tool == AnnotationTool.Note) {
            if (annotations?.ready == true) editing.update { it.copy(noteEditor = NoteEditor(pageIndex, PdfPoint(x, y), null)) }
            return
        }
        if (annotations?.ready == true) {
            annotationAt(annotations.own, annotations.foreign, pageIndex, PdfPoint(x, y))?.let { target ->
                editing.update { it.copy(menu = target) }
                return
            }
        }
        val document = openedDocument() ?: return
        viewModelScope.launch {
            if (documentHasForm && !tools.annotating) {
                val fields = formFields.value[pageIndex] ?: fieldsOf(document, pageIndex).also { loaded ->
                    formFields.update { it + (pageIndex to loaded) }
                }
                formFieldAt(fields, PdfPoint(x, y))?.let { field ->
                    onFormFieldTapped(field)
                    return@launch
                }
            }
            val link = runCatchingCancellable { document.linkAt(pageIndex, x, y) }.getOrNull()
            when (val action = linkAction(link, document.pageCount)) {
                is LinkAction.Jump -> onJump(action.jump)
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

    /** Moves to [jump] from contents, bookmarks, a link or a page number; the start is remembered. */
    fun onJump(jump: PageJump) {
        val from = position ?: ReadingPosition(pageIndex = currentPage.value)
        jumpHistory.update { it.jumped(from, jump.pageIndex) }
        linkTargets.update { it.copy(jump = jump) }
    }

    /** Back to where the last jump started. */
    fun onJumpBack() {
        val origin = jumpHistory.value.origin ?: return
        val pageCount = openedDocument()?.pageCount ?: return
        val page = origin.pageIndex.coerceIn(0, pageCount - 1)
        jumpHistory.value = JumpHistory()
        // A fraction of the page as laid out: right however the pages are turned or cropped.
        linkTargets.update { it.copy(jump = PageJump(page, offsetFraction = origin.pageOffsetFraction)) }
    }

    fun onJumpHandled() = linkTargets.update { it.copy(jump = null) }

    fun onExternalLinkHandled() = linkTargets.update { it.copy(externalLink = null) }

    fun onPositionChanged(position: ReadingPosition) {
        this.position = position
        if (currentPage.value != position.pageIndex) loadFormFields(position.pageIndex)
        currentPage.value = position.pageIndex
        positionUpdates.tryEmit(position)
        jumpHistory.update { it.scrolledTo(position.pageIndex) }
        // A side panel stays open while reading; its highlight follows along.
        if (toc.value.isOpen) toc.update { it.copy(currentIndex = currentOutlineIndex(it.entries, position)) }
    }

    fun onOpenToc() {
        fullScreen.value = false
        val opened = (documentState.value as? DocumentState.Opened)?.opened ?: return
        val at = position ?: opened.position
        toc.update { state -> state.copy(isOpen = true, currentIndex = currentOutlineIndex(state.entries, at)) }
    }

    /** The outline entry the reader is in, or -1. */
    private fun currentOutlineIndex(entries: List<OutlineEntry>, at: ReadingPosition): Int {
        val document = openedDocument() ?: return -1
        // The position is the fraction scrolled into the top page; convert to points.
        val pageHeight = document.pageSizes.getOrNull(at.pageIndex)?.heightPt ?: 0
        return OutlineLocator.currentIndex(entries, at.pageIndex, at.pageOffsetFraction * pageHeight) ?: -1
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

    fun onVolumeKeysChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setVolumeKeysTurnPages(enabled) }
    }

    fun onTwoPageSpreadsChange(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setTwoPageSpreads(enabled) }
    }

    /** Marks up the selected text and clears the selection. */
    fun onMarkup(kind: MarkupKind) {
        val selected = selection.value?.toUiState() ?: return
        val controller = controller.value?.takeIf { it.state.value.ready } ?: return
        selection.value = null
        viewModelScope.launch {
            controller.add(markupFor(selected, kind, preferencesRepository.preferences.first().markupColor))
        }
    }

    fun onAnnotationMenuDismissed() = editing.update { it.copy(menu = null) }

    /** Deletes TilePDF's annotation or hides another app's until the next save. */
    fun onDeleteAnnotation(target: AnnotationTarget) {
        val controller = controller.value ?: return
        when (target) {
            is AnnotationTarget.Own -> controller.remove(target.annotation.id)
            is AnnotationTarget.Foreign -> controller.removeForeign(target.annotation.ref)
        }
        editing.update { it.copy(menu = null) }
    }

    fun onEditNote(note: Note) = editing.update { it.copy(menu = null, noteEditor = NoteEditor(note.pageIndex, note.at, note)) }

    /** Saves the note being written; blank text adds nothing, or deletes the note being edited. */
    fun onNoteSubmitted(text: String) {
        val editor = editing.value.noteEditor ?: return
        val controller = controller.value ?: return
        editing.update { it.copy(noteEditor = null) }
        val edited = editor.editing
        when {
            edited != null && text.isBlank() -> controller.remove(edited.id)
            edited != null -> controller.replace(edited.id, edited.copy(id = newAnnotationId(), text = text))
            text.isNotBlank() -> viewModelScope.launch {
                val color = preferencesRepository.preferences.first().markupColor
                controller.add(Note(newAnnotationId(), editor.pageIndex, color, editor.at, text))
            }
        }
    }

    fun onNoteDeleted() {
        val edited = editing.value.noteEditor?.editing ?: return
        controller.value?.remove(edited.id)
        editing.update { it.copy(noteEditor = null) }
    }

    fun onNoteDismissed() = editing.update { it.copy(noteEditor = null) }

    fun onUndo() {
        controller.value?.undo()
    }

    /** Shows or hides the annotation toolbar; hiding it goes back to reading. */
    fun onToggleAnnotating() = editing.update {
        if (it.annotating) AnnotationEditing() else it.copy(annotating = true, tool = AnnotationTool.Select)
    }

    fun onToolSelected(tool: AnnotationTool) {
        // The pen and eraser need both hands free of a selection.
        if (tool == AnnotationTool.Pen || tool == AnnotationTool.Eraser) selection.value = null
        editing.update { it.copy(tool = tool) }
    }

    fun onMarkupColorSelected(color: Int) {
        viewModelScope.launch { preferencesRepository.setMarkupColor(color) }
    }

    fun onPenColorSelected(color: Int) {
        viewModelScope.launch { preferencesRepository.setPenColor(color) }
    }

    fun onPenWidthSelected(widthPt: Float) {
        viewModelScope.launch { preferencesRepository.setPenWidth(widthPt) }
    }

    /** A finished pen stroke on [pageIndex], in top-left points. */
    fun onInkStroke(pageIndex: Int, points: List<PdfPoint>) {
        if (points.isEmpty()) return
        val controller = controller.value?.takeIf { it.state.value.ready } ?: return
        viewModelScope.launch {
            val preferences = preferencesRepository.preferences.first()
            controller.add(Ink(newAnnotationId(), pageIndex, preferences.penColor, preferences.penWidthPt, listOf(points)))
        }
    }

    /** Opens the signature pad, with the saved signature when there is one. */
    fun onAddSignature() {
        if (controller.value?.state?.value?.ready != true) return
        viewModelScope.launch { signatureStep.value = SignatureStep.Pad(signatureStore.load()) }
    }

    /** The pad's strokes, in any units: kept for next time, then placed with a tap. */
    fun onSignatureDrawn(strokes: List<List<PdfPoint>>) {
        val signature = normalizeSignature(strokes)
        if (signature == null) {
            signatureStep.value = null
            return
        }
        signatureStep.value = SignatureStep.Place(signature)
        viewModelScope.launch { signatureStore.save(signature) }
    }

    fun onSignatureCancelled() {
        signatureStep.value = null
    }

    /** An ink drawing like the pen's, so it undoes, erases and saves the same way. */
    private fun placeSignature(signature: Signature, pageIndex: Int, centre: PdfPoint) {
        signatureStep.value = null
        val controller = controller.value?.takeIf { it.state.value.ready } ?: return
        controller.add(
            Ink(newAnnotationId(), pageIndex, SIGNATURE_COLOR, SIGNATURE_PEN_PT, signature.placedAround(centre, SIGNATURE_WIDTH_PT)),
        )
    }

    /** Everything one eraser gesture crossed, removed as one undo step. */
    fun onErase(erased: Erased) {
        controller.value?.removeAll(erased.ids, erased.refs)
    }

    fun onRedo() {
        controller.value?.redo()
    }

    /** Selects the word at a point on a page, given in top-left PDF points. */
    fun onLongPress(pageIndex: Int, x: Float, y: Float) {
        // One-finger drags draw or erase: holding still must not select text.
        if (editing.value.drawing) return
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
        // Writes and the save request run on the application scope: they outlive this screen.
        controller.value?.let {
            it.requestSave()
            it.close()
        }
        retireReplacedDocument()
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

    private data class Outputs(
        val annotations: AnnotationsUiState,
        val editing: AnnotationEditing,
        val results: Pair<SaveCopyResult?, HandOff?>,
        val jumpHistory: JumpHistory,
        val forms: Triple<Map<Int, List<FormField>>, FormField?, SignatureStep?>,
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
            val view: DocumentView,
            /** Pages as [view] shows them; the image sources draw through these. */
            val transforms: List<PageViewTransform>,
        ) : DocumentState
    }
}
