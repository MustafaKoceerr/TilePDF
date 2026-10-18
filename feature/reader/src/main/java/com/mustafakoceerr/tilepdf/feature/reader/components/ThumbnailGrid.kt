package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.feature.reader.R

private val MinCellWidth = 112.dp
private val CellSpacing = 12.dp
private val PageShape = RoundedCornerShape(4.dp)

/**
 * Every page as a thumbnail; tapping one picks it. Only items on screen load
 * their image, and scrolling an item away cancels its render.
 */
@Composable
internal fun ThumbnailGrid(
    pageSizes: List<PageSize>,
    /** Outlined, and scrolled into view on open. */
    currentPage: Int,
    thumbnail: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap,
    onSelect: (pageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = currentPage.coerceIn(0, pageSizes.lastIndex.coerceAtLeast(0)))
    LazyVerticalGrid(
        columns = GridCells.Adaptive(MinCellWidth),
        state = gridState,
        contentPadding = PaddingValues(CellSpacing),
        horizontalArrangement = Arrangement.spacedBy(CellSpacing),
        verticalArrangement = Arrangement.spacedBy(CellSpacing),
        modifier = modifier,
    ) {
        itemsIndexed(pageSizes) { pageIndex, pageSize ->
            ThumbnailItem(
                pageIndex = pageIndex,
                pageSize = pageSize,
                isCurrent = pageIndex == currentPage,
                thumbnail = thumbnail,
                onClick = { onSelect(pageIndex) },
            )
        }
    }
}

@Composable
private fun ThumbnailItem(
    pageIndex: Int,
    pageSize: PageSize,
    isCurrent: Boolean,
    thumbnail: suspend (pageIndex: Int, widthPx: Int) -> ImageBitmap,
    onClick: () -> Unit,
) {
    var widthPx by remember { mutableIntStateOf(0) }
    var image by remember(pageIndex) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pageIndex, widthPx) {
        if (widthPx > 0) image = thumbnail(pageIndex, widthPx)
    }

    val description = stringResource(R.string.reader_page_description, pageIndex + 1)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .semantics { selected = isCurrent }
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(pageSize.aspectRatio)
                .clip(PageShape)
                // White like the reader's pages while the image loads.
                .background(Color.White)
                .then(
                    if (isCurrent) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, PageShape)
                    else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, PageShape),
                )
                .onSizeChanged { widthPx = it.width },
        ) {
            image?.let {
                Image(
                    bitmap = it,
                    contentDescription = description,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = (pageIndex + 1).toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
