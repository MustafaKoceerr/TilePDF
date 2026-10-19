package com.mustafakoceerr.tilepdf.feature.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.print.PrintManager
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import com.mustafakoceerr.tilepdf.feature.reader.components.tocPlacement
import com.mustafakoceerr.tilepdf.feature.reader.components.TocPlacement
import com.mustafakoceerr.tilepdf.feature.reader.components.TableOfContentsPanel
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mustafakoceerr.tilepdf.feature.reader.components.FormFieldDialog
import com.mustafakoceerr.tilepdf.feature.reader.components.LinkConfirmDialog
import com.mustafakoceerr.tilepdf.feature.reader.components.NoTextChip
import com.mustafakoceerr.tilepdf.feature.reader.components.RecognitionProgressBar
import com.mustafakoceerr.tilepdf.feature.reader.components.RecognizeTextDialog
import com.mustafakoceerr.tilepdf.feature.reader.components.ScriptDialogView
import com.mustafakoceerr.tilepdf.feature.reader.components.CertificateChoiceDialog
import com.mustafakoceerr.tilepdf.feature.reader.components.SignatureHint
import com.mustafakoceerr.tilepdf.feature.reader.components.SigningProgress
import com.mustafakoceerr.tilepdf.data.signature.SignFailure
import com.mustafakoceerr.tilepdf.data.signature.SignResult
import com.mustafakoceerr.tilepdf.feature.reader.components.SignaturesChip
import com.mustafakoceerr.tilepdf.feature.reader.components.SignaturesSheet
import com.mustafakoceerr.tilepdf.feature.reader.components.SignaturePadDialog
import com.mustafakoceerr.tilepdf.feature.reader.components.showsSpreads
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafakoceerr.tilepdf.core.common.KeyEventHost
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationMenuDialog
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTool
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationToolbar
import com.mustafakoceerr.tilepdf.feature.reader.annotation.Erased
import com.mustafakoceerr.tilepdf.feature.reader.annotation.PageDrawing
import com.mustafakoceerr.tilepdf.feature.reader.annotation.ToolbarState
import com.mustafakoceerr.tilepdf.feature.reader.annotation.annotationListItems
import com.mustafakoceerr.tilepdf.feature.reader.annotation.topPt
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTarget
import com.mustafakoceerr.tilepdf.feature.reader.annotation.NoteDialog
import com.mustafakoceerr.tilepdf.feature.reader.annotation.SelectionMarkupBar
import com.mustafakoceerr.tilepdf.feature.reader.components.ReaderErrorContent
import com.mustafakoceerr.tilepdf.feature.reader.components.rememberTouchExplorationEnabled
import com.mustafakoceerr.tilepdf.feature.reader.components.PageJump
import com.mustafakoceerr.tilepdf.feature.reader.components.PasswordDialog
import com.mustafakoceerr.tilepdf.feature.reader.components.PdfPageList
import com.mustafakoceerr.tilepdf.feature.reader.components.PdfPagePager
import com.mustafakoceerr.tilepdf.feature.reader.components.ReaderSettingsSheet
import com.mustafakoceerr.tilepdf.feature.reader.components.SearchTopBar
import com.mustafakoceerr.tilepdf.feature.reader.components.TableOfContentsSheet
import com.mustafakoceerr.tilepdf.feature.reader.components.ThumbnailGrid
import com.mustafakoceerr.tilepdf.feature.reader.components.TrimMemoryEffect
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.SaveError
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReadingMode
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    onOpenDocument: (uri: String) -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    // Leaving the app saves; leaving the reader saves in onCleared.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onStop() }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(
        uiState = uiState,
        onBack = onBack,
        onPositionChanged = viewModel::onPositionChanged,
        searchActions = SearchActions(
            onOpen = viewModel::onOpenSearch,
            onQueryChange = viewModel::onSearchQueryChange,
            onPrevious = viewModel::onPreviousMatch,
            onNext = viewModel::onNextMatch,
            onClose = viewModel::onCloseSearch,
        ),
        onLongPress = viewModel::onLongPress,
        onDragSelectionHandle = viewModel::onDragSelectionHandle,
        onSelectAllOnPage = viewModel::onSelectAllOnPage,
        onClearSelection = viewModel::onClearSelection,
        onOpenToc = viewModel::onOpenToc,
        onCloseToc = viewModel::onCloseToc,
        onPasswordEntered = viewModel::onPasswordEntered,
        onRetryOpen = viewModel::onRetryOpen,
        onTapPage = viewModel::onTap,
        onTapOutsidePage = viewModel::onTapOutsidePage,
        onJump = viewModel::onJump,
        onJumpBack = viewModel::onJumpBack,
        onJumpHandled = viewModel::onJumpHandled,
        onExternalLinkHandled = viewModel::onExternalLinkHandled,
        onLinkConfirmed = viewModel::onLinkConfirmed,
        onLinkDismissed = viewModel::onLinkDismissed,
        onToggleBookmark = viewModel::onToggleBookmark,
        onPageThemeSelected = viewModel::onPageThemeSelected,
        onReadingModeSelected = viewModel::onReadingModeSelected,
        onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
        onVolumeKeysChange = viewModel::onVolumeKeysChange,
        onTwoPageSpreadsChange = viewModel::onTwoPageSpreadsChange,
        onRotate = viewModel::onRotate,
        onFormValueSubmitted = viewModel::onFormValueSubmitted,
        onAddSignature = viewModel::onAddSignature,
        onScriptAnswer = viewModel::onScriptAnswer,
        onOpenSignatures = viewModel::onOpenSignatures,
        certificateActions = CertificateActions(
            onStart = viewModel::onSignWithCertificate,
            onChosen = viewModel::onCertificateChosen,
            onCancel = viewModel::onCertificateCancelled,
            onResultShown = viewModel::onCertificateResultShown,
        ),
        onCloseSignatures = viewModel::onCloseSignatures,
        recognitionActions = RecognitionActions(
            onRequest = viewModel::onRecognizeTextRequested,
            onRecognize = viewModel::onRecognizeText,
            onDismissChoice = viewModel::onRecognizeChoiceDismissed,
            onCancel = viewModel::onRecognitionCancelled,
            onResultShown = viewModel::onRecognitionResultShown,
        ),
        onSignatureDrawn = viewModel::onSignatureDrawn,
        onSignatureCancelled = viewModel::onSignatureCancelled,
        onFormEditorDismissed = viewModel::onFormEditorDismissed,
        onCropMarginsChange = viewModel::onCropMarginsChange,
        annotationActions = AnnotationActions(
            onMarkup = viewModel::onMarkup,
            onMenuDismissed = viewModel::onAnnotationMenuDismissed,
            onDelete = viewModel::onDeleteAnnotation,
            onEditNote = viewModel::onEditNote,
            onNoteSubmitted = viewModel::onNoteSubmitted,
            onNoteDeleted = viewModel::onNoteDeleted,
            onNoteDismissed = viewModel::onNoteDismissed,
            onUndo = viewModel::onUndo,
            onRedo = viewModel::onRedo,
            onToggleAnnotating = viewModel::onToggleAnnotating,
            onTool = viewModel::onToolSelected,
            onMarkupColor = viewModel::onMarkupColorSelected,
            onPenColor = viewModel::onPenColorSelected,
            onPenWidth = viewModel::onPenWidthSelected,
            onInkStroke = viewModel::onInkStroke,
            onErase = viewModel::onErase,
            onSaveCopy = viewModel::onSaveCopy,
            onSaveCopyResultHandled = viewModel::onSaveCopyResultHandled,
            onRetrySave = viewModel::onRetrySave,
            onShare = viewModel::onShare,
            onPrint = viewModel::onPrint,
            onHandOffHandled = viewModel::onHandOffHandled,
        ),
        onOpenDocument = onOpenDocument,
    )
}

/** Selection events for the page list; copying is done by the screen itself. */
internal class SelectionActions(
    val onLongPress: (pageIndex: Int, x: Float, y: Float) -> Unit,
    val onDragHandle: (SelectionHandle, x: Float, y: Float) -> Unit,
    val onCopy: () -> Unit,
    val onSelectAll: () -> Unit,
    val onClear: () -> Unit,
)

/** Annotation events, grouped like [SearchActions]. */
internal class AnnotationActions(
    val onMarkup: (MarkupKind) -> Unit,
    val onMenuDismissed: () -> Unit,
    val onDelete: (AnnotationTarget) -> Unit,
    val onEditNote: (Note) -> Unit,
    val onNoteSubmitted: (String) -> Unit,
    val onNoteDeleted: () -> Unit,
    val onNoteDismissed: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onToggleAnnotating: () -> Unit = {},
    val onTool: (AnnotationTool) -> Unit = {},
    val onMarkupColor: (Int) -> Unit = {},
    val onPenColor: (Int) -> Unit = {},
    val onPenWidth: (Float) -> Unit = {},
    val onInkStroke: (pageIndex: Int, List<PdfPoint>) -> Unit = { _, _ -> },
    val onErase: (Erased) -> Unit = {},
    val onSaveCopy: (target: String) -> Unit = {},
    val onSaveCopyResultHandled: () -> Unit = {},
    val onRetrySave: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onPrint: () -> Unit = {},
    val onHandOffHandled: () -> Unit = {},
) {
    companion object {
        val None = AnnotationActions({}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/** Search events, grouped to keep the screen's signature readable. */
internal class SearchActions(
    val onOpen: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onClose: () -> Unit,
) {
    companion object {
        val None = SearchActions({}, {}, {}, {}, {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onPositionChanged: (ReadingPosition) -> Unit,
    searchActions: SearchActions,
    onLongPress: (pageIndex: Int, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onDragSelectionHandle: (SelectionHandle, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onSelectAllOnPage: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onOpenToc: () -> Unit = {},
    onCloseToc: () -> Unit = {},
    onPasswordEntered: (String) -> Unit = {},
    onRetryOpen: () -> Unit = {},
    onTapPage: (pageIndex: Int, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onTapOutsidePage: () -> Unit = {},
    onJump: (PageJump) -> Unit = {},
    onJumpBack: () -> Unit = {},
    onJumpHandled: () -> Unit = {},
    onExternalLinkHandled: () -> Unit = {},
    onLinkConfirmed: () -> Unit = {},
    onLinkDismissed: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onPageThemeSelected: (PageTheme) -> Unit = {},
    onReadingModeSelected: (ReadingMode) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    onVolumeKeysChange: (Boolean) -> Unit = {},
    onTwoPageSpreadsChange: (Boolean) -> Unit = {},
    onRotate: () -> Unit = {},
    onFormValueSubmitted: (String) -> Unit = {},
    onAddSignature: () -> Unit = {},
    onScriptAnswer: (ScriptAnswer) -> Unit = {},
    onOpenSignatures: () -> Unit = {},
    certificateActions: CertificateActions = CertificateActions.None,
    onCloseSignatures: () -> Unit = {},
    recognitionActions: RecognitionActions = RecognitionActions.None,
    onSignatureDrawn: (List<List<PdfPoint>>) -> Unit = {},
    onSignatureCancelled: () -> Unit = {},
    onFormEditorDismissed: () -> Unit = {},
    onCropMarginsChange: (Boolean) -> Unit = {},
    annotationActions: AnnotationActions = AnnotationActions.None,
    onOpenDocument: (uri: String) -> Unit = {},
) {
    val ready = uiState as? ReaderUiState.Ready
    val saveCopyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { target ->
        target?.let { annotationActions.onSaveCopy(it.toString()) }
    }
    val saveCopy = { ready?.let { saveCopyLauncher.launch(copyName(it.title)) } }
    SaveCopyResultEffect(ready?.saveCopyResult, onOpenDocument, annotationActions.onSaveCopyResultHandled)
    HandOffEffect(ready?.handOff, annotationActions.onHandOffHandled)
    RecognitionResultEffect(ready?.recognition?.result, recognitionActions.onResultShown)
    CertificateResultEffect(ready?.certificateResult, certificateActions.onResultShown)
    if (ready?.certificateStep == CertificateStep.Choose) {
        CertificateChoiceDialog(onChosen = certificateActions.onChosen, onDismiss = certificateActions.onCancel)
    }
    BackHandler(enabled = ready?.certificateStep is CertificateStep.Place, onBack = certificateActions.onCancel)
    if (ready?.signatures?.open == true) {
        SignaturesSheet(ready.signatures.checks, onDismiss = onCloseSignatures)
    }
    ready?.scriptDialog?.let { dialog ->
        // A new dialog starts afresh, even with the same text.
        key(dialog) { ScriptDialogView(dialog, onAnswer = onScriptAnswer) }
    }
    ready?.linkToConfirm?.let { link ->
        LinkConfirmDialog(link, onOpen = onLinkConfirmed, onDismiss = onLinkDismissed)
    }
    if (ready?.recognition?.choosing == true) {
        RecognizeTextDialog(onRecognize = recognitionActions.onRecognize, onDismiss = recognitionActions.onDismissChoice)
    }
    val search = ready?.search
    val selection = ready?.selection
    val copySelection = rememberCopyAction(selection?.text, onCopied = onClearSelection)
    // Before the others: clearing a selection or closing search comes first.
    val canJumpBack = ready?.backToPage != null && !ready.editing.annotating
    BackHandler(enabled = canJumpBack, onBack = onJumpBack)
    BackHandler(enabled = selection != null, onBack = onClearSelection)
    var goToPageOpen by rememberSaveable { mutableStateOf(false) }
    var pagesOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var pageJump by remember { mutableStateOf<PageJump?>(null) }
    LaunchedEffect(ready?.jump) {
        ready?.jump?.let { pageJump = it }
        if (ready?.jump != null) onJumpHandled()
    }
    val context = LocalContext.current
    LaunchedEffect(ready?.externalLink) {
        val link = ready?.externalLink ?: return@LaunchedEffect
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.reader_no_app_for_link, Toast.LENGTH_SHORT).show()
        }
        onExternalLinkHandled()
    }
    BackHandler(enabled = search?.isActive == true, onBack = searchActions.onClose)
    KeepScreenOnEffect(ready?.preferences?.keepScreenOn == true)

    // A TalkBack user can't find bars hidden by a tap, so they stay.
    val fullScreen = ready?.isFullScreen == true && !rememberTouchExplorationEnabled()
    SystemBarsEffect(hidden = fullScreen)

    val topBar: @Composable () -> Unit = {
        if (search?.isActive == true) {
            SearchTopBar(
                state = search,
                onQueryChange = searchActions.onQueryChange,
                onPrevious = searchActions.onPrevious,
                onNext = searchActions.onNext,
                onClose = searchActions.onClose,
            )
        } else {
            TopAppBar(
                title = {
                    Text(
                        text = ready?.title ?: stringResource(R.string.reader_untitled),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(TilePdfIcons.ArrowBack),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
                actions = {
                    if (ready != null) {
                        IconButton(onClick = { pagesOpen = true }) {
                            Icon(
                                painter = painterResource(TilePdfIcons.Grid),
                                contentDescription = stringResource(R.string.reader_pages),
                            )
                        }
                        // Always there: it also holds the bookmarks.
                        if (ready.annotations.ready && !ready.isXfa) {
                            IconButton(onClick = annotationActions.onToggleAnnotating) {
                                Icon(
                                    painter = painterResource(TilePdfIcons.Edit),
                                    contentDescription = stringResource(R.string.reader_annotate),
                                )
                            }
                        }
                        IconButton(onClick = if (ready.toc.isOpen) onCloseToc else onOpenToc) {
                            Icon(
                                painter = painterResource(TilePdfIcons.Toc),
                                contentDescription = stringResource(R.string.reader_toc),
                            )
                        }
                        IconButton(onClick = searchActions.onOpen) {
                            Icon(
                                painter = painterResource(TilePdfIcons.Search),
                                contentDescription = stringResource(R.string.reader_search),
                            )
                        }
                        OverflowMenu(
                            expanded = menuOpen,
                            onExpandedChange = { menuOpen = it },
                            bookmarked = ready.isCurrentPageBookmarked,
                            onToggleBookmark = onToggleBookmark,
                            onGoToPage = { goToPageOpen = true },
                            onOpenSettings = { settingsOpen = true },
                            onSaveCopy = { saveCopy() },
                            onShare = annotationActions.onShare,
                            onPrint = annotationActions.onPrint,
                            cropMargins = ready.documentView.cropMargins,
                            onAddSignature = onAddSignature,
                            recognizing = ready.recognition.progress != null,
                            pagesArePdf = !ready.isXfa,
                            signed = ready.signatures.checks.isNotEmpty(),
                            onSignWithCertificate = certificateActions.onStart,
                            onOpenSignatures = onOpenSignatures,
                            onRecognizeText = recognitionActions.onRequest,
                            onRotate = onRotate,
                            onCropMarginsChange = onCropMarginsChange,
                        )
                    }
                },
            )
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Decided here, from the space the reader has, and used only in here.
        val placement = tocPlacement(maxWidth.value, maxHeight.value)
        val tocAsPanel = placement == TocPlacement.Side && ready?.toc?.isOpen == true
        BackHandler(enabled = tocAsPanel, onBack = onCloseToc)
        Row(Modifier.fillMaxSize()) {
            if (tocAsPanel) {
                // Choosing something jumps there; the panel stays for the next choice.
                TableOfContentsPanel(
                    outline = ready.toc.entries,
                    currentIndex = ready.toc.currentIndex,
                    bookmarks = ready.toc.bookmarks,
                    onSelect = { entry -> onJump(PageJump(entry.pageIndex, entry.topPt)) },
                    onSelectPage = { page -> onJump(PageJump(page)) },
                    onClose = onCloseToc,
                    annotations = remember(ready.annotations) { annotationListItems(ready.annotations.own, ready.annotations.foreign) },
                    onSelectAnnotation = { item -> onJump(PageJump(item.pageIndex, item.topPt)) },
                    onDeleteAnnotation = annotationActions.onDelete,
                )
            }
            // The page pane: everything the reader showed before, now beside the panel.
            Box(Modifier.weight(1f).fillMaxHeight()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Edge-to-edge: the keyboard overlaps unless we shrink for it,
                        // and search would scroll matches behind the keyboard.
                        .imePadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    when (uiState) {
                        ReaderUiState.Loading -> CircularProgressIndicator()
                        is ReaderUiState.Error -> ReaderErrorContent(
                            error = uiState.reason,
                            onFileChosen = onOpenDocument,
                            onRetry = onRetryOpen,
                            onBack = onBack,
                        )
                        is ReaderUiState.PasswordRequired -> PasswordDialog(
                            wrongPassword = uiState.wrongPassword,
                            onSubmit = onPasswordEntered,
                            onCancel = onBack,
                        )
                        is ReaderUiState.Ready -> key(uiState.preferences.readingMode) {
                            ReaderPages(
                                ready = uiState,
                                goToPageOpen = goToPageOpen,
                                onGoToPageOpenChange = { goToPageOpen = it },
                                onGoToPage = { onJump(PageJump(it)) },
                                pageJump = pageJump,
                                onPageJumpHandled = { pageJump = null },
                                // Page turns aren't jumps: no way back is offered for them.
                                onTurnPage = { pageJump = PageJump(it) },
                                onPositionChanged = onPositionChanged,
                                onTapPage = onTapPage,
                                onTapOutsidePage = onTapOutsidePage,
                                annotationActions = annotationActions,
                                selectionActions = SelectionActions(
                                    onLongPress = onLongPress,
                                    onDragHandle = onDragSelectionHandle,
                                    onCopy = copySelection,
                                    onSelectAll = onSelectAllOnPage,
                                    onClear = onClearSelection,
                                ),
                            )
                        }
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (ready?.signatureStep is SignatureStep.Place) {
                        SignatureHint(onCancel = onSignatureCancelled, modifier = Modifier.navigationBarsPadding())
                    }
                    when (ready?.certificateStep) {
                        is CertificateStep.Place -> SignatureHint(
                            onCancel = certificateActions.onCancel,
                            text = stringResource(R.string.certificate_place_hint),
                            modifier = Modifier.navigationBarsPadding(),
                        )
                        CertificateStep.Signing -> SigningProgress(Modifier.navigationBarsPadding())
                        else -> Unit
                    }
                    val recognition = ready?.recognition
                    val signatureSummary = ready?.signatures?.summary
                    val quiet = selection == null && ready?.editing?.annotating != true && ready?.signatureStep == null &&
                        !(ready?.backToPage != null && canJumpBack)
                    if (signatureSummary != null && ready.currentPage == 0 && quiet && recognition?.progress == null) {
                        SignaturesChip(signatureSummary, onClick = onOpenSignatures, modifier = Modifier.navigationBarsPadding())
                    }
                    if (recognition?.progress != null) {
                        RecognitionProgressBar(recognition.progress, onCancel = recognitionActions.onCancel, modifier = Modifier.navigationBarsPadding())
                    } else if (
                        recognition?.currentPageHasNoText == true && selection == null && !ready.editing.annotating &&
                        ready.signatureStep == null && !(ready.backToPage != null && canJumpBack)
                    ) {
                        NoTextChip(onClick = recognitionActions.onRequest, modifier = Modifier.navigationBarsPadding())
                    }
                    val backToPage = ready?.backToPage
                    // Kept while the chip fades out, after the way back is forgotten.
                    var fadingBackToPage by remember { mutableStateOf(backToPage) }
                    if (backToPage != null) fadingBackToPage = backToPage
                    AnimatedVisibility(
                        visible = backToPage != null && canJumpBack && selection == null,
                        enter = fadeIn(),
                        exit = fadeOut(tween(JUMP_BACK_FADE_MS)),
                    ) {
                        JumpBackChip(
                            pageNumber = (fadingBackToPage ?: 0) + 1,
                            onClick = onJumpBack,
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                    if (selection != null) {
                        SelectionMarkupBar(enabled = ready.annotations.ready, onMarkup = annotationActions.onMarkup)
                    }
                    if (ready != null && ready.editing.annotating) {
                        AnnotationToolbar(
                            state = ToolbarState(
                                tool = ready.editing.tool,
                                markupColor = ready.preferences.markupColor,
                                penColor = ready.preferences.penColor,
                                penWidthPt = ready.preferences.penWidthPt,
                                canUndo = ready.annotations.canUndo,
                                canRedo = ready.annotations.canRedo,
                            ),
                            onTool = annotationActions.onTool,
                            onMarkupColor = annotationActions.onMarkupColor,
                            onPenColor = annotationActions.onPenColor,
                            onPenWidth = annotationActions.onPenWidth,
                            onUndo = annotationActions.onUndo,
                            onRedo = annotationActions.onRedo,
                            onDone = annotationActions.onToggleAnnotating,
                        )
                    }
                }
                // Over the pages, not above them: hiding it doesn't move the pages.
                // Qualified: inside the panel row, the RowScope overload would be picked.
                androidx.compose.animation.AnimatedVisibility(
                    visible = !fullScreen,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                ) {
                    Column {
                        topBar()
                        val error = ready?.annotations?.saveError
                        if (error != null && error != SaveError.NeedsPassword) {
                            SaveErrorBanner(
                                error = error,
                                onSaveCopy = { saveCopy() },
                                onRetry = annotationActions.onRetrySave,
                            )
                        }
                    }
                }
            }
        }
        if (ready != null && ready.toc.isOpen && placement == TocPlacement.Sheet) {
            TableOfContentsSheet(
                outline = ready.toc.entries,
                currentIndex = ready.toc.currentIndex,
                bookmarks = ready.toc.bookmarks,
                onSelect = { entry ->
                    onCloseToc()
                    onJump(PageJump(entry.pageIndex, entry.topPt))
                },
                onSelectPage = { page ->
                    onCloseToc()
                    onJump(PageJump(page))
                },
                onDismiss = onCloseToc,
                annotations = remember(ready.annotations) { annotationListItems(ready.annotations.own, ready.annotations.foreign) },
                onSelectAnnotation = { item ->
                    onCloseToc()
                    onJump(PageJump(item.pageIndex, item.topPt))
                },
                onDeleteAnnotation = annotationActions.onDelete,
            )
        }
    }

    (ready?.signatureStep as? SignatureStep.Pad)?.let { step ->
        SignaturePadDialog(saved = step.saved, onPlace = onSignatureDrawn, onDismiss = onSignatureCancelled)
    }
    BackHandler(enabled = ready?.signatureStep is SignatureStep.Place, onBack = onSignatureCancelled)
    ready?.formEditor?.let { field ->
        // A new field starts from its own value, not the last dialog's.
        key(field) { FormFieldDialog(field, onSubmit = onFormValueSubmitted, onDismiss = onFormEditorDismissed) }
    }
    ready?.editing?.menu?.let { target ->
        AnnotationMenuDialog(
            target = target,
            onDelete = { annotationActions.onDelete(target) },
            onEditNote = annotationActions.onEditNote,
            onDismiss = annotationActions.onMenuDismissed,
        )
    }
    ready?.editing?.noteEditor?.let { editor ->
        // A new editor starts from its own text, not the last dialog's.
        key(editor) {
            NoteDialog(
                initial = editor.editing?.text.orEmpty(),
                editing = editor.editing != null,
                onSave = annotationActions.onNoteSubmitted,
                onDelete = annotationActions.onNoteDeleted,
                onDismiss = annotationActions.onNoteDismissed,
            )
        }
    }

    if (ready != null) {
        val thumbnails = ready.thumbnails
        TrimMemoryEffect(thumbnails) { thumbnails.trimMemory() }
        if (settingsOpen) {
            ReaderSettingsSheet(
                preferences = ready.preferences,
                onPageThemeSelected = onPageThemeSelected,
                onReadingModeSelected = onReadingModeSelected,
                onKeepScreenOnChange = onKeepScreenOnChange,
                onVolumeKeysChange = onVolumeKeysChange,
                onTwoPageSpreadsChange = onTwoPageSpreadsChange,
                onDismiss = { settingsOpen = false },
            )
        }
        if (pagesOpen) {
            PagesDialog(
                ready = ready,
                onSelect = { page ->
                    pagesOpen = false
                    onJump(PageJump(page))
                },
                onDismiss = { pagesOpen = false },
            )
        }
    }
}

/** The pages, scrolled or swiped as the reading mode says. */
@Composable
private fun ReaderPages(
    ready: ReaderUiState.Ready,
    goToPageOpen: Boolean,
    onGoToPageOpenChange: (Boolean) -> Unit,
    onGoToPage: (pageIndex: Int) -> Unit,
    pageJump: PageJump?,
    onPageJumpHandled: () -> Unit,
    onTurnPage: (pageIndex: Int) -> Unit,
    onPositionChanged: (ReadingPosition) -> Unit,
    onTapPage: (pageIndex: Int, x: Float, y: Float) -> Unit,
    onTapOutsidePage: () -> Unit,
    annotationActions: AnnotationActions,
    selectionActions: SelectionActions,
) {
    val annotations = ready.annotations.own.groupBy { it.pageIndex }
    val drawingFor: ((Int) -> PageDrawing)? = if (ready.editing.drawing) {
        val foreign = ready.annotations.foreign.groupBy { it.pageIndex }
        val preferences = ready.preferences
        { page ->
            PageDrawing(
                tool = ready.editing.tool,
                penColor = preferences.penColor,
                penWidthPt = preferences.penWidthPt,
                foreign = foreign[page].orEmpty(),
                onStroke = { annotationActions.onInkStroke(page, it) },
                onErase = annotationActions.onErase,
            )
        }
    } else {
        null
    }
    // From the space the pages have, not the device: a side panel or split screen counts.
    var spreadSpace by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val twoPageSpreads = with(density) {
        showsSpreads(ready.preferences.twoPageSpreads, spreadSpace.width.toDp().value, spreadSpace.height.toDp().value)
    }
    Box(Modifier.fillMaxSize().onSizeChanged { spreadSpace = it })
    // Tools on the page take taps then, so fields aren't marked as fillable.
    val formFields = if (ready.editing.annotating) emptyMap() else ready.formFields
    VolumeKeyPagingEffect(
        enabled = ready.preferences.volumeKeysTurnPages,
        currentPage = ready.currentPage,
        pageCount = ready.pageSizes.size,
        spreads = twoPageSpreads && ready.preferences.readingMode == ReadingMode.Horizontal,
        onTurn = onTurnPage,
    )
    when (ready.preferences.readingMode) {
        ReadingMode.Vertical -> PdfPageList(
            pageSizes = ready.pageSizes,
            pageTransforms = ready.pageTransforms,
            formFields = formFields,
            pageImages = ready.pageImages,
            initialPosition = ready.initialPosition,
            pageTheme = ready.preferences.pageTheme,
            matches = ready.search.matches,
            currentMatch = ready.search.currentMatch,
            onPositionChanged = onPositionChanged,
            goToPageOpen = goToPageOpen,
            onGoToPageOpenChange = onGoToPageOpenChange,
            onGoToPage = onGoToPage,
            pageJump = pageJump,
            onPageJumpHandled = onPageJumpHandled,
            onTapPage = onTapPage,
            onTapOutsidePage = onTapOutsidePage,
            chromePadding = chromePadding(),
            selection = ready.selection,
            selectionActions = selectionActions,
            annotations = annotations,
            drawingFor = drawingFor,
        )
        ReadingMode.Horizontal -> PdfPagePager(
            pageSizes = ready.pageSizes,
            pageTransforms = ready.pageTransforms,
            twoPageSpreads = twoPageSpreads,
            formFields = formFields,
            pageImages = ready.pageImages,
            initialPage = ready.initialPosition.pageIndex,
            pageTheme = ready.preferences.pageTheme,
            matches = ready.search.matches,
            currentMatch = ready.search.currentMatch,
            onPageChanged = { onPositionChanged(ReadingPosition(pageIndex = it)) },
            goToPageOpen = goToPageOpen,
            onGoToPageOpenChange = onGoToPageOpenChange,
            onGoToPage = onGoToPage,
            pageJump = pageJump,
            onPageJumpHandled = onPageJumpHandled,
            onTapPage = onTapPage,
            onTapOutsidePage = onTapOutsidePage,
            chromePadding = chromePadding(),
            selection = ready.selection,
            selectionActions = selectionActions,
            annotations = annotations,
            drawingFor = drawingFor,
        )
    }
}

/** The page grid, full screen over the reader. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PagesDialog(ready: ReaderUiState.Ready, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    // Edge to edge like the reader; the Scaffold pads for the system bars.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        // The dialog has its own window: give it bar icons that suit the theme.
        val view = LocalView.current
        val lightBars = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightBars
                isAppearanceLightNavigationBars = lightBars
            }
        }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.reader_pages)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                painter = painterResource(TilePdfIcons.Close),
                                contentDescription = stringResource(R.string.reader_close),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            ThumbnailGrid(
                pageSizes = ready.pageSizes,
                currentPage = ready.currentPage,
                thumbnail = ready.thumbnails::thumbnail,
                onSelect = onSelect,
                modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            )
        }
    }
}

/** Says why annotations aren't in the file, and offers a copy (or another try). */
@Composable
private fun SaveErrorBanner(error: SaveError, onSaveCopy: () -> Unit, onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Text(
                text = stringResource(
                    when (error) {
                        SaveError.NoWritePermission -> R.string.save_error_no_permission
                        SaveError.FileChanged -> R.string.save_error_file_changed
                        else -> R.string.save_error_failed
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (error == SaveError.Failed) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.save_retry)) }
            } else {
                TextButton(onClick = onSaveCopy) { Text(stringResource(R.string.reader_save_copy)) }
            }
        }
    }
}

/** Opens a saved copy, or says the copy failed; then forgets the result. */
@Composable
private fun SaveCopyResultEffect(result: SaveCopyResult?, onOpenDocument: (String) -> Unit, onHandled: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(result) {
        when (result) {
            null -> return@LaunchedEffect
            is SaveCopyResult.Saved -> {
                Toast.makeText(context, R.string.save_copy_done, Toast.LENGTH_SHORT).show()
                onOpenDocument(result.uri)
            }
            SaveCopyResult.Failed -> Toast.makeText(context, R.string.save_error_failed, Toast.LENGTH_LONG).show()
        }
        onHandled()
    }
}

private const val PDF_MIME_TYPE = "application/pdf"

/** What signing with a certificate can be asked to do. */
internal data class CertificateActions(
    val onStart: () -> Unit,
    val onChosen: (alias: String?) -> Unit,
    val onCancel: () -> Unit,
    val onResultShown: () -> Unit,
) {
    companion object {
        val None = CertificateActions({}, {}, {}, {})
    }
}

/** Tells how signing with a certificate went; then forgets it. */
@Composable
private fun CertificateResultEffect(result: SignResult?, onShown: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(result) {
        val message = when (result) {
            null -> return@LaunchedEffect
            SignResult.Signed -> R.string.certificate_signed
            is SignResult.Failed -> when (result.reason) {
                SignFailure.KeyUnavailable -> R.string.certificate_key_unavailable
                SignFailure.NoWritePermission -> R.string.certificate_no_write
                SignFailure.Encrypted -> R.string.certificate_encrypted
                SignFailure.Failed -> R.string.certificate_failed
            }
        }
        Toast.makeText(context, message, if (result == SignResult.Signed) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        onShown()
    }
}

/** What text recognition on scanned pages can be asked to do. */
internal data class RecognitionActions(
    val onRequest: () -> Unit,
    val onRecognize: (allPages: Boolean) -> Unit,
    val onDismissChoice: () -> Unit,
    val onCancel: () -> Unit,
    val onResultShown: () -> Unit,
) {
    companion object {
        val None = RecognitionActions({}, {}, {}, {}, {})
    }
}

/** Tells how a finished recognition went; then forgets it. */
@Composable
private fun RecognitionResultEffect(result: RecognitionResult?, onShown: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(result) {
        when (result) {
            null -> return@LaunchedEffect
            is RecognitionResult.Done -> {
                val message = if (result.pagesWithText == 0) {
                    resources.getString(R.string.recognize_nothing_found)
                } else {
                    resources.getQuantityString(R.plurals.recognize_done, result.pagesWithText, result.pagesWithText)
                }
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
            is RecognitionResult.AlreadyHasText -> Toast.makeText(
                context,
                if (result.allPages) R.string.recognize_all_have_text else R.string.recognize_page_has_text,
                Toast.LENGTH_SHORT,
            ).show()
            RecognitionResult.Failed -> Toast.makeText(context, R.string.recognize_failed, Toast.LENGTH_LONG).show()
        }
        onShown()
    }
}

/** Opens the share sheet or the print dialog for an exported document; then forgets it. */
@Composable
private fun HandOffEffect(handOff: HandOff?, onHandled: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(handOff) {
        when (handOff) {
            null -> return@LaunchedEffect
            is HandOff.Share -> share(context, handOff)
            is HandOff.Print -> print(context, handOff)
            HandOff.Failed -> Toast.makeText(context, R.string.reader_hand_off_failed, Toast.LENGTH_LONG).show()
        }
        onHandled()
    }
}

private fun share(context: Context, share: HandOff.Share) {
    val uri = share.uri.toUri()
    val send = Intent(Intent.ACTION_SEND)
        .setType(PDF_MIME_TYPE)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_TITLE, share.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // The chooser previews the file; the receiver gets read access only.
        .apply { clipData = ClipData.newRawUri(share.title, uri) }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.reader_no_app_for_share, Toast.LENGTH_SHORT).show()
    }
}

private fun print(context: Context, print: HandOff.Print) {
    val printManager = context.getSystemService(PrintManager::class.java) ?: return
    printManager.print(print.title, PdfPrintAdapter(context.applicationContext, print.uri.toUri(), print.title, print.pageCount), null)
}

/** "report.pdf" → "report (açıklamalı).pdf", the name offered for a copy. */
private fun copyName(title: String) = "${title.removeSuffix(".pdf").removeSuffix(".PDF")} (açıklamalı).pdf"

/**
 * Room the bars take at the top and bottom, whether or not they are showing,
 * so the pages keep their place when the bars hide.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun chromePadding(): PaddingValues {
    val statusBar = WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    val navigationBar = WindowInsets.navigationBarsIgnoringVisibility.asPaddingValues().calculateBottomPadding()
    return PaddingValues(top = statusBar + TopAppBarDefaults.TopAppBarExpandedHeight, bottom = navigationBar)
}

/** Hides the status and navigation bars while [hidden]; a swipe from the edge shows them briefly. */
@Composable
private fun SystemBarsEffect(hidden: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, hidden) {
        val controller = WindowCompat.getInsetsController(window, view)
        if (hidden) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
        // Leaving the reader always brings them back.
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** Stops the screen from timing out while [enabled] and the reader is shown. */
/** While [enabled], volume keys turn pages instead of changing the volume. */
@Composable
private fun VolumeKeyPagingEffect(enabled: Boolean, currentPage: Int, pageCount: Int, spreads: Boolean, onTurn: (Int) -> Unit) {
    val host = LocalActivity.current as? KeyEventHost ?: return
    val page by rememberUpdatedState(currentPage)
    val turn by rememberUpdatedState(onTurn)
    DisposableEffect(host, enabled, pageCount, spreads) {
        if (enabled && pageCount > 0) {
            host.setKeyHandler { event ->
                val target = pageForVolumeKey(event.keyCode, page, pageCount, spreads) ?: return@setKeyHandler false
                // Both down and up are taken; held down, it keeps turning.
                if (event.action == KeyEvent.ACTION_DOWN && target != page) turn(target)
                true
            }
        }
        onDispose { host.setKeyHandler(null) }
    }
}

@Composable
private fun KeepScreenOnEffect(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/**
 * Copies [text] to the clipboard. Android 13+ confirms copies itself; older
 * versions get a toast so the user knows it worked.
 */
@Composable
private fun rememberCopyAction(text: String?, onCopied: () -> Unit): () -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentText by rememberUpdatedState(text)
    val currentOnCopied by rememberUpdatedState(onCopied)
    return remember {
        {
            currentText?.let { copied ->
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(copied.take(40), copied)))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, R.string.reader_copied, Toast.LENGTH_SHORT).show()
                    }
                    currentOnCopied()
                }
            }
        }
    }
}

/** How slowly the "Back to page" chip fades once its time is up. */
private const val JUMP_BACK_FADE_MS = 600

/** "12. sayfaya dön": back to where the last jump started. */
@Composable
private fun JumpBackChip(pageNumber: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.reader_jump_back, pageNumber)) },
        leadingIcon = { Icon(painterResource(TilePdfIcons.ArrowBack), contentDescription = null) },
        modifier = modifier.padding(bottom = 16.dp),
    )
}

/** Less frequent actions, so the title keeps its room in the top bar. */
@Composable
private fun OverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onGoToPage: () -> Unit,
    onOpenSettings: () -> Unit,
    onSaveCopy: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    cropMargins: Boolean,
    onAddSignature: () -> Unit,
    recognizing: Boolean,
    /** False on an XFA form, whose pages can't take a signature or recognised text. */
    pagesArePdf: Boolean,
    signed: Boolean,
    onOpenSignatures: () -> Unit,
    onSignWithCertificate: () -> Unit,
    onRecognizeText: () -> Unit,
    onRotate: () -> Unit,
    onCropMarginsChange: (Boolean) -> Unit,
) {
    Box {
        IconButton(onClick = { onExpandedChange(true) }) {
            Icon(
                painter = painterResource(TilePdfIcons.MoreVert),
                contentDescription = stringResource(R.string.reader_more_options),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            DropdownMenuItem(
                text = {
                    Text(stringResource(if (bookmarked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add))
                },
                leadingIcon = {
                    Icon(
                        painterResource(if (bookmarked) TilePdfIcons.Bookmark else TilePdfIcons.BookmarkBorder),
                        contentDescription = null,
                    )
                },
                onClick = {
                    onExpandedChange(false)
                    onToggleBookmark()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_go_to_page)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Tag), contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onGoToPage()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_add_signature)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Signature), contentDescription = null) },
                enabled = pagesArePdf,
                onClick = {
                    onExpandedChange(false)
                    onAddSignature()
                },
            )
            if (signed) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.reader_signatures)) },
                    leadingIcon = { Icon(painterResource(TilePdfIcons.Verified), contentDescription = null) },
                    onClick = {
                        onExpandedChange(false)
                        onOpenSignatures()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_recognize_text)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.TextScan), contentDescription = null) },
                enabled = !recognizing && pagesArePdf,
                onClick = {
                    onExpandedChange(false)
                    onRecognizeText()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_sign_certificate)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Verified), contentDescription = null) },
                enabled = pagesArePdf,
                onClick = {
                    onExpandedChange(false)
                    onSignWithCertificate()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_rotate)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.RotateRight), contentDescription = null) },
                onClick = {
                    // Stays open: turning again is one more tap.
                    onRotate()
                },
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text(stringResource(R.string.reader_crop_margins))
                        Text(
                            stringResource(R.string.reader_crop_margins_recommended),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Crop), contentDescription = null) },
                trailingIcon = { Checkbox(checked = cropMargins, onCheckedChange = null) },
                onClick = {
                    onExpandedChange(false)
                    onCropMarginsChange(!cropMargins)
                },
                modifier = Modifier.semantics { toggleableState = ToggleableState(cropMargins) },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_share)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Share), contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onShare()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_print)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Print), contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onPrint()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_save_copy)) },
                onClick = {
                    onExpandedChange(false)
                    onSaveCopy()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_settings)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Tune), contentDescription = null) },
                onClick = {
                    onExpandedChange(false)
                    onOpenSettings()
                },
            )
        }
    }
}

@Preview
@Composable
private fun ReaderScreenErrorPreview() {
    TilePDFTheme {
        ReaderScreen(ReaderUiState.Error(ReaderError.Corrupt), {}, {}, SearchActions.None)
    }
}
