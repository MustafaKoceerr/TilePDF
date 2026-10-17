package com.mustafakoceerr.tilepdf.feature.reader

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationMenuDialog
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTool
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationToolbar
import com.mustafakoceerr.tilepdf.feature.reader.annotation.Erased
import com.mustafakoceerr.tilepdf.feature.reader.annotation.PageDrawing
import com.mustafakoceerr.tilepdf.feature.reader.annotation.ToolbarState
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTarget
import com.mustafakoceerr.tilepdf.feature.reader.annotation.NoteDialog
import com.mustafakoceerr.tilepdf.feature.reader.annotation.SelectionMarkupBar
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
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReadingMode
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import kotlinx.coroutines.launch

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
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
        onTapPage = viewModel::onTap,
        onTapOutsidePage = viewModel::onTapOutsidePage,
        onJumpHandled = viewModel::onJumpHandled,
        onExternalLinkHandled = viewModel::onExternalLinkHandled,
        onToggleBookmark = viewModel::onToggleBookmark,
        onPageThemeSelected = viewModel::onPageThemeSelected,
        onReadingModeSelected = viewModel::onReadingModeSelected,
        onKeepScreenOnChange = viewModel::onKeepScreenOnChange,
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
        ),
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
    onTapPage: (pageIndex: Int, x: Float, y: Float) -> Unit = { _, _, _ -> },
    onTapOutsidePage: () -> Unit = {},
    onJumpHandled: () -> Unit = {},
    onExternalLinkHandled: () -> Unit = {},
    onToggleBookmark: () -> Unit = {},
    onPageThemeSelected: (PageTheme) -> Unit = {},
    onReadingModeSelected: (ReadingMode) -> Unit = {},
    onKeepScreenOnChange: (Boolean) -> Unit = {},
    annotationActions: AnnotationActions = AnnotationActions.None,
) {
    val ready = uiState as? ReaderUiState.Ready
    val search = ready?.search
    val selection = ready?.selection
    val copySelection = rememberCopyAction(selection?.text, onCopied = onClearSelection)
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

    val fullScreen = ready?.isFullScreen == true
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
                        if (ready.annotations.ready) {
                            IconButton(onClick = annotationActions.onToggleAnnotating) {
                                Icon(
                                    painter = painterResource(TilePdfIcons.Edit),
                                    contentDescription = stringResource(R.string.reader_annotate),
                                )
                            }
                        }
                        IconButton(onClick = onOpenToc) {
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
                        )
                    }
                },
            )
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                is ReaderUiState.Error -> Text(
                    text = stringResource(uiState.reason.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
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
                        pageJump = pageJump,
                        onPageJumpHandled = { pageJump = null },
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
        Column(Modifier.align(Alignment.BottomCenter)) {
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
        AnimatedVisibility(
            visible = !fullScreen,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
        ) {
            topBar()
        }
    }

    if (ready != null && ready.toc.isOpen) {
        TableOfContentsSheet(
            outline = ready.toc.entries,
            currentIndex = ready.toc.currentIndex,
            bookmarks = ready.toc.bookmarks,
            onSelect = { entry ->
                onCloseToc()
                pageJump = PageJump(entry.pageIndex, entry.topPt)
            },
            onSelectPage = { page ->
                onCloseToc()
                pageJump = PageJump(page)
            },
            onDismiss = onCloseToc,
        )
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
                onDismiss = { settingsOpen = false },
            )
        }
        if (pagesOpen) {
            PagesDialog(
                ready = ready,
                onSelect = { page ->
                    pagesOpen = false
                    pageJump = PageJump(page)
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
    pageJump: PageJump?,
    onPageJumpHandled: () -> Unit,
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
    when (ready.preferences.readingMode) {
        ReadingMode.Vertical -> PdfPageList(
            pageSizes = ready.pageSizes,
            pageImages = ready.pageImages,
            initialPosition = ready.initialPosition,
            pageTheme = ready.preferences.pageTheme,
            matches = ready.search.matches,
            currentMatch = ready.search.currentMatch,
            onPositionChanged = onPositionChanged,
            goToPageOpen = goToPageOpen,
            onGoToPageOpenChange = onGoToPageOpenChange,
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
            pageImages = ready.pageImages,
            initialPage = ready.initialPosition.pageIndex,
            pageTheme = ready.preferences.pageTheme,
            matches = ready.search.matches,
            currentMatch = ready.search.currentMatch,
            onPageChanged = { onPositionChanged(ReadingPosition(pageIndex = it)) },
            goToPageOpen = goToPageOpen,
            onGoToPageOpenChange = onGoToPageOpenChange,
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

/** Less frequent actions, so the title keeps its room in the top bar. */
@Composable
private fun OverflowMenu(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    bookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onGoToPage: () -> Unit,
    onOpenSettings: () -> Unit,
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

@get:StringRes
private val ReaderError.messageRes: Int
    get() = when (this) {
        ReaderError.NotFound -> R.string.reader_error_not_found
        ReaderError.Corrupt -> R.string.reader_error_corrupt
    }

@Preview
@Composable
private fun ReaderScreenErrorPreview() {
    TilePDFTheme {
        ReaderScreen(ReaderUiState.Error(ReaderError.Corrupt), {}, {}, SearchActions.None)
    }
}
