package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationKind
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationListItem
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTarget
import com.mustafakoceerr.tilepdf.feature.reader.annotation.labelRes
import com.mustafakoceerr.tilepdf.model.Bookmark
import com.mustafakoceerr.tilepdf.model.OutlineEntry

private val IndentPerLevel = 16.dp

/** Deep outlines would squeeze titles into nothing; indent stops growing here. */
private const val MAX_INDENT_LEVEL = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableOfContentsSheet(
    outline: List<OutlineEntry>,
    /** Where the reader is: highlighted, and scrolled into view on open. */
    currentIndex: Int,
    bookmarks: List<Bookmark>,
    onSelect: (OutlineEntry) -> Unit,
    onSelectPage: (pageIndex: Int) -> Unit,
    onDismiss: () -> Unit,
    annotations: List<AnnotationListItem> = emptyList(),
    onSelectAnnotation: (AnnotationListItem) -> Unit = {},
    onDeleteAnnotation: (AnnotationTarget) -> Unit = {},
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TocContent(outline, currentIndex, bookmarks, onSelect, onSelectPage, annotations, onSelectAnnotation, onDeleteAnnotation)
    }
}

/**
 * The same tabs as [TableOfContentsSheet], in a panel beside the pages on
 * large screens. Choosing something doesn't close it; [onClose] does.
 */
@Composable
internal fun TableOfContentsPanel(
    outline: List<OutlineEntry>,
    currentIndex: Int,
    bookmarks: List<Bookmark>,
    onSelect: (OutlineEntry) -> Unit,
    onSelectPage: (pageIndex: Int) -> Unit,
    onClose: () -> Unit,
    annotations: List<AnnotationListItem> = emptyList(),
    onSelectAnnotation: (AnnotationListItem) -> Unit = {},
    onDeleteAnnotation: (AnnotationTarget) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 1.dp, modifier = modifier.width(TocPanelWidth).fillMaxHeight()) {
        TocContent(
            outline, currentIndex, bookmarks, onSelect, onSelectPage, annotations, onSelectAnnotation, onDeleteAnnotation,
            modifier = Modifier.statusBarsPadding().navigationBarsPadding(),
            header = {
                // Its own row: beside the tabs it would squeeze "Annotations" onto two lines.
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) {
                        Icon(painterResource(TilePdfIcons.Close), contentDescription = stringResource(R.string.reader_toc_close))
                    }
                }
            },
        )
    }
}

/** Tabs for contents, bookmarks and annotations, with [header] above them. */
@Composable
private fun TocContent(
    outline: List<OutlineEntry>,
    currentIndex: Int,
    bookmarks: List<Bookmark>,
    onSelect: (OutlineEntry) -> Unit,
    onSelectPage: (pageIndex: Int) -> Unit,
    annotations: List<AnnotationListItem>,
    onSelectAnnotation: (AnnotationListItem) -> Unit,
    onDeleteAnnotation: (AnnotationTarget) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    // Documents without an outline open straight on their bookmarks.
    var tab by rememberSaveable { mutableIntStateOf(if (outline.isEmpty()) BOOKMARKS_TAB else CONTENTS_TAB) }
    Column(modifier) {
        header?.invoke()
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == CONTENTS_TAB,
                onClick = { tab = CONTENTS_TAB },
                text = { Text(stringResource(R.string.reader_toc)) },
            )
            Tab(
                selected = tab == BOOKMARKS_TAB,
                onClick = { tab = BOOKMARKS_TAB },
                text = { Text(stringResource(R.string.reader_bookmarks)) },
            )
            Tab(
                selected = tab == ANNOTATIONS_TAB,
                onClick = { tab = ANNOTATIONS_TAB },
                text = { Text(stringResource(R.string.reader_annotations)) },
            )
        }
        when (tab) {
            CONTENTS_TAB -> OutlineList(outline, currentIndex, onSelect)
            BOOKMARKS_TAB -> BookmarkList(bookmarks, onSelectPage)
            else -> AnnotationList(annotations, onSelectAnnotation, onDeleteAnnotation)
        }
    }
}

@Composable
private fun OutlineList(outline: List<OutlineEntry>, currentIndex: Int, onSelect: (OutlineEntry) -> Unit) {
    // A couple of entries above the current one stay visible for context.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))
    LazyColumn(state = listState) {
        itemsIndexed(outline) { index, entry ->
            val isCurrent = index == currentIndex
            ListItem(
                headlineContent = {
                    Text(
                        text = entry.title,
                        fontWeight = if (entry.depth == 0 || isCurrent) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // Indent the text, not the row, so a highlighted
                        // row is coloured edge to edge.
                        modifier = Modifier.padding(
                            start = IndentPerLevel * entry.depth.coerceAtMost(MAX_INDENT_LEVEL),
                        ),
                    )
                },
                trailingContent = { Text((entry.pageIndex + 1).toString()) },
                colors = if (isCurrent) {
                    ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        headlineColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                } else {
                    ListItemDefaults.colors()
                },
                modifier = Modifier
                    .semantics { selected = isCurrent }
                    .clickable { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun BookmarkList(bookmarks: List<Bookmark>, onSelectPage: (Int) -> Unit) {
    if (bookmarks.isEmpty()) {
        Text(
            text = stringResource(R.string.reader_no_bookmarks),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn {
        items(bookmarks, key = { it.pageIndex }) { bookmark ->
            ListItem(
                headlineContent = { Text(stringResource(R.string.reader_page_description, bookmark.pageIndex + 1)) },
                leadingContent = { Icon(painterResource(TilePdfIcons.Bookmark), contentDescription = null) },
                modifier = Modifier.clickable { onSelectPage(bookmark.pageIndex) },
            )
        }
    }
}

/** Every annotation in reading order; tap to go there, or delete it. */
@Composable
private fun AnnotationList(
    items: List<AnnotationListItem>,
    onSelect: (AnnotationListItem) -> Unit,
    onDelete: (AnnotationTarget) -> Unit,
) {
    if (items.isEmpty()) {
        Text(
            text = stringResource(R.string.reader_no_annotations),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(24.dp),
        )
        return
    }
    LazyColumn {
        items(items) { item ->
            ListItem(
                headlineContent = { Text(stringResource(item.kind.labelRes)) },
                overlineContent = { Text(stringResource(R.string.annotation_page, item.pageIndex + 1)) },
                supportingContent = if (item.text.isBlank()) null else {
                    { Text(item.text, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                },
                leadingContent = { Icon(painterResource(item.kind.icon), contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onDelete(item.target) }) {
                        Icon(painterResource(TilePdfIcons.Delete), contentDescription = stringResource(R.string.annotation_delete))
                    }
                },
                modifier = Modifier.clickable { onSelect(item) },
            )
        }
    }
}

@get:DrawableRes
private val AnnotationKind.icon: Int
    get() = when (this) {
        AnnotationKind.Note -> TilePdfIcons.StickyNote
        AnnotationKind.Ink -> TilePdfIcons.Edit
        else -> TilePdfIcons.TextSelect
    }

private const val CONTENTS_TAB = 0
private const val BOOKMARKS_TAB = 1
private const val ANNOTATIONS_TAB = 2
