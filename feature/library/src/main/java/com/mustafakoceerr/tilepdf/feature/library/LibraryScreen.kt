package com.mustafakoceerr.tilepdf.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.LibrarySort
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme

private const val PDF_MIME_TYPE = "application/pdf"

/** A library row needs about this much width; wider screens get more columns. */
private val MinRowWidth = 360.dp

/** The floating button's height and margin, with a little air. */
private val FabClearance = 88.dp

@Composable
fun LibraryScreen(
    onOpenDocument: (uri: String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        actions = LibraryActions(
            onDocumentPicked = { uri ->
                viewModel.onDocumentPicked(uri)
                onOpenDocument(uri)
            },
            onOpenDocument = onOpenDocument,
            onToggleFavorite = viewModel::onToggleFavorite,
            onRemove = viewModel::onRemove,
            onUndoRemove = viewModel::onUndoRemove,
            onRemoveConfirmed = viewModel::onRemoveConfirmed,
            onSearchOpen = viewModel::onSearchOpen,
            onSearchClose = viewModel::onSearchClose,
            onQueryChange = viewModel::onQueryChange,
            onFilterSelected = viewModel::onFilterSelected,
            onSortSelected = viewModel::onSortSelected,
            onOpenSettings = onOpenSettings,
        ),
    )
}

/** Library events, grouped to keep the screen's signature readable. */
internal class LibraryActions(
    val onDocumentPicked: (uri: String) -> Unit = {},
    val onOpenDocument: (uri: String) -> Unit = {},
    val onToggleFavorite: (uri: String) -> Unit = {},
    val onRemove: (uri: String) -> Unit = {},
    val onUndoRemove: () -> Unit = {},
    val onRemoveConfirmed: () -> Unit = {},
    val onSearchOpen: () -> Unit = {},
    val onSearchClose: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onFilterSelected: (LibraryFilter) -> Unit = {},
    val onSortSelected: (LibrarySort) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(uiState: LibraryUiState, actions: LibraryActions) {
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { actions.onDocumentPicked(it.toString()) } }
    val success = uiState as? LibraryUiState.Success
    val snackbars = remember { SnackbarHostState() }
    RemovalSnackbarEffect(success?.pendingRemoval, snackbars, actions)
    // The bar steps aside while scrolling down and comes back on the way up.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val gridState = rememberLazyGridState()
    val showingList = success?.items?.isNotEmpty() == true
    // With no list to drag, a bar that stepped aside could never be brought back.
    LaunchedEffect(showingList) { if (!showingList) scrollBehavior.state.heightOffset = 0f }
    // At the top, or heading back up, the button says what it does; otherwise it's just the icon.
    val scrolledDown by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 && !gridState.lastScrolledBackward } }
    val fabExpanded = !showingList || !scrolledDown

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            if (success?.searching == true) {
                SearchBar(success.query, actions, scrollBehavior)
            } else {
                TopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = { Text(stringResource(R.string.library_title)) },
                    actions = {
                        if (success?.hasDocuments == true) {
                            IconButton(onClick = actions.onSearchOpen) {
                                Icon(painterResource(TilePdfIcons.Search), contentDescription = stringResource(R.string.library_search))
                            }
                            SortMenu(success.sort, actions.onSortSelected)
                        }
                        if (success != null) LibraryMenu(onSettings = actions.onOpenSettings)
                    },
                )
            }
        },
        floatingActionButton = {
            val label = stringResource(R.string.library_open_pdf)
            ExtendedFloatingActionButton(
                onClick = { openDocument.launch(arrayOf(PDF_MIME_TYPE)) },
                expanded = fabExpanded,
                // Folded, the icon alone has to name the button.
                icon = { Icon(painterResource(TilePdfIcons.Add), contentDescription = if (fabExpanded) null else label) },
                text = { Text(label) },
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                success == null -> CircularProgressIndicator()
                !success.hasDocuments -> EmptyState(TilePdfIcons.Document, R.string.library_empty_title, R.string.library_empty)
                else -> Column(Modifier.fillMaxSize()) {
                    FilterChips(success.filter, actions.onFilterSelected)
                    when {
                        success.items.isNotEmpty() -> DocumentList(success.items, gridState, actions)
                        success.filter == LibraryFilter.Favorites && success.query.isBlank() -> EmptyState(
                            TilePdfIcons.StarBorder,
                            R.string.library_no_favorites,
                            R.string.library_no_favorites_hint,
                            Modifier.fillMaxWidth(),
                        )
                        else -> EmptyState(
                            TilePdfIcons.Search,
                            R.string.library_no_results,
                            R.string.library_no_results_hint,
                            Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows "removed, undo" for the pending removal. Undo restores the row;
 * the snackbar ending any other way commits the removal.
 */
@Composable
private fun RemovalSnackbarEffect(pending: String?, snackbars: SnackbarHostState, actions: LibraryActions) {
    val message = stringResource(R.string.library_removed)
    val undo = stringResource(R.string.library_undo)
    val currentActions by rememberUpdatedState(actions)
    LaunchedEffect(pending) {
        if (pending == null) return@LaunchedEffect
        val result = snackbars.showSnackbar(message, actionLabel = undo, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) currentActions.onUndoRemove() else currentActions.onRemoveConfirmed()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(query: String, actions: LibraryActions, scrollBehavior: TopAppBarScrollBehavior) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    TopAppBar(
        scrollBehavior = scrollBehavior,
        navigationIcon = {
            IconButton(onClick = actions.onSearchClose) {
                Icon(painterResource(TilePdfIcons.ArrowBack), contentDescription = stringResource(R.string.library_search_close))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = actions.onQueryChange,
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
    )
}

@Composable
private fun SortMenu(sort: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(TilePdfIcons.Sort), contentDescription = stringResource(R.string.library_sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = { RadioButton(selected = option == sort, onClick = null) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LibraryMenu(onSettings: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(TilePdfIcons.MoreVert), contentDescription = stringResource(R.string.library_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_settings)) },
                onClick = {
                    open = false
                    onSettings()
                },
            )
        }
    }
}

@Composable
private fun FilterChips(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
    ) {
        LibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes)) },
            )
        }
    }
}

/** Why there is nothing to show and what to do about it, under an [icon] that sets the scene. */
@Composable
private fun EmptyState(@DrawableRes icon: Int, @StringRes title: Int, @StringRes body: Int, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.padding(32.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 16.dp)
                .semantics { heading() },
        )
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DocumentList(items: List<LibraryItem>, gridState: LazyGridState, actions: LibraryActions) {
    val top = items.firstOrNull()?.document?.uri
    var shownTop by remember { mutableStateOf(top) }
    LaunchedEffect(top) {
        if (revealsNewTopRow(shownTop, top, gridState.firstVisibleItemIndex)) gridState.scrollToItem(0)
        shownTop = top
    }
    // One column on phones; wide screens fit several rows side by side.
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MinRowWidth),
        state = gridState,
        // Room to scroll the last row out from under the Open PDF button.
        contentPadding = PaddingValues(bottom = FabClearance),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items, key = { it.document.uri }) { item ->
            val document = item.document
            ListItem(
                headlineContent = { Text(document.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                leadingContent = { DocumentThumbnail(item.thumbnail, dimmed = !item.isAccessible) },
                supportingContent = {
                    if (item.isAccessible) {
                        Column {
                            Text(stringResource(R.string.library_page_progress, document.position.pageIndex + 1, document.pageCount))
                            readingProgress(document.position.pageIndex, document.pageCount)?.let { progress ->
                                LinearProgressIndicator(
                                    progress = { progress },
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth()
                                        // The page line above already says it.
                                        .clearAndSetSemantics {},
                                )
                            }
                        }
                    } else {
                        Text(text = stringResource(R.string.library_no_access), color = MaterialTheme.colorScheme.error)
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconToggleButton(checked = document.favorite, onCheckedChange = { actions.onToggleFavorite(document.uri) }) {
                            Icon(
                                painter = painterResource(if (document.favorite) TilePdfIcons.Star else TilePdfIcons.StarBorder),
                                contentDescription = stringResource(
                                    if (document.favorite) R.string.library_favorite_remove else R.string.library_favorite_add,
                                ),
                            )
                        }
                        RowMenu(document.displayName, onRemove = { actions.onRemove(document.uri) })
                    }
                },
                modifier = Modifier.clickable { actions.onOpenDocument(document.uri) },
            )
        }
    }
}

@Composable
private fun RowMenu(documentName: String, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(TilePdfIcons.MoreVert), contentDescription = stringResource(R.string.library_row_options, documentName))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_remove)) },
                leadingIcon = { Icon(painterResource(TilePdfIcons.Delete), contentDescription = null) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

@get:StringRes
private val LibrarySort.labelRes: Int
    get() = when (this) {
        LibrarySort.Recent -> R.string.library_sort_recent
        LibrarySort.Name -> R.string.library_sort_name
        LibrarySort.Added -> R.string.library_sort_added
    }

@get:StringRes
private val LibraryFilter.labelRes: Int
    get() = when (this) {
        LibraryFilter.All -> R.string.library_all
        LibraryFilter.Favorites -> R.string.library_favorites
    }

@Preview
@Composable
private fun LibraryScreenPreview() {
    TilePDFTheme {
        LibraryScreen(
            uiState = LibraryUiState.Success(
                listOf(
                    LibraryItem(
                        LibraryDocument("content://1", "Kotlin in Action.pdf", 360, ReadingPosition(41), 0, favorite = true),
                        isAccessible = true,
                    ),
                    LibraryItem(
                        LibraryDocument("content://2", "Clean Architecture.pdf", 432, ReadingPosition(), 0),
                        isAccessible = false,
                    ),
                )
            ),
            actions = LibraryActions(),
        )
    }
}

@Preview
@Composable
private fun LibraryScreenEmptyPreview() {
    TilePDFTheme {
        LibraryScreen(LibraryUiState.Success(emptyList()), LibraryActions())
    }
}
