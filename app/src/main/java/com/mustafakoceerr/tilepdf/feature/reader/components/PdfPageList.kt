package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.model.ReadingPosition

/** Drafts are rendered at a fraction of the screen width to keep them cheap. */
private const val DRAFT_SCALE = 0.5f

private val PAGE_GAP = 8.dp

/** Inverts colours without re-rendering: white paper becomes black. */
private val NightModeFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    )
)

/** Zoomable vertical list of all pages. */
@Composable
fun PdfPageList(
    pageSizes: List<PageSize>,
    pageImages: PageImageSource,
    initialPosition: ReadingPosition,
    nightMode: Boolean,
    onPositionChanged: (ReadingPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val draftWidthPx = (constraints.maxWidth * DRAFT_SCALE).toInt()
        // TODO: also restore pageOffsetFraction and zoom.
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialPosition.pageIndex)
        val zoomState = rememberZoomState()

        LaunchedEffect(listState, zoomState) {
            snapshotFlow {
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                ReadingPosition(
                    pageIndex = listState.firstVisibleItemIndex,
                    pageOffsetFraction = first?.let {
                        listState.firstVisibleItemScrollOffset.toFloat() / it.size
                    } ?: 0f,
                    zoom = zoomState.scale,
                )
            }.collect(onPositionChanged)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(zoomState, listState),
            // Horizontal padding scales with zoom so the whole content grows
            // proportionally; otherwise page heights would grow by a slightly
            // different factor than the zoom and the scroll anchor would drift.
            contentPadding = PaddingValues(
                horizontal = PAGE_GAP * zoomState.scale,
                vertical = PAGE_GAP,
            ),
            verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
        ) {
            items(count = pageSizes.size, key = { it }) { pageIndex ->
                PdfPage(
                    pageIndex = pageIndex,
                    pageSize = pageSizes[pageIndex],
                    draftWidthPx = draftWidthPx,
                    pageImages = pageImages,
                    nightMode = nightMode,
                )
            }
        }
    }
}

@Composable
private fun PdfPage(
    pageIndex: Int,
    pageSize: PageSize,
    draftWidthPx: Int,
    pageImages: PageImageSource,
    nightMode: Boolean,
) {
    // Seed from the cache so pages scrolled back into view don't flash blank.
    var bitmap by remember(pageIndex, draftWidthPx) {
        mutableStateOf(pageImages.cached(pageIndex, draftWidthPx))
    }
    // Cancelled automatically when the item leaves the composition.
    LaunchedEffect(pageIndex, draftWidthPx) {
        if (bitmap == null) bitmap = pageImages.load(pageIndex, draftWidthPx)
    }

    // Sized from the page's aspect ratio up front so the layout never jumps.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.aspectRatio)
            .background(if (nightMode) Color.Black else Color.White),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.reader_page_description, pageIndex + 1),
                contentScale = ContentScale.FillBounds,
                colorFilter = if (nightMode) NightModeFilter else null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
