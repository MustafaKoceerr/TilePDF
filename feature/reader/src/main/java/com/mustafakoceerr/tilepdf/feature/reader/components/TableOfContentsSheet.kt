package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.feature.reader.R
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
    onSelect: (OutlineEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    // A couple of entries above the current one stay visible for context.
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 2).coerceAtLeast(0))

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.reader_toc),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
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
}
