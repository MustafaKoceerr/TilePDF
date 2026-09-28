package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.model.OutlineEntry

private val IndentPerLevel = 16.dp

/** Deep outlines would squeeze titles into nothing; indent stops growing here. */
private const val MAX_INDENT_LEVEL = 4

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TableOfContentsSheet(
    outline: List<OutlineEntry>,
    onSelect: (OutlineEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.reader_toc),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(outline) { entry ->
                ListItem(
                    headlineContent = {
                        Text(
                            text = entry.title,
                            fontWeight = if (entry.depth == 0) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = { Text((entry.pageIndex + 1).toString()) },
                    modifier = Modifier
                        .clickable { onSelect(entry) }
                        .padding(start = IndentPerLevel * entry.depth.coerceAtMost(MAX_INDENT_LEVEL)),
                )
            }
        }
    }
}
