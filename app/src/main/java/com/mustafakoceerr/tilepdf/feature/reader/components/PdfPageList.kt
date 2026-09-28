package com.mustafakoceerr.tilepdf.feature.reader.components

import android.content.ComponentCallbacks2
import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.core.render.RenderedTile
import com.mustafakoceerr.tilepdf.core.render.Tile
import com.mustafakoceerr.tilepdf.core.render.TileGrid
import com.mustafakoceerr.tilepdf.core.render.tilesFor
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

/** Drafts are rendered at a fraction of the screen width to keep them cheap. */
private const val DRAFT_SCALE = 0.5f

/** Wait for scrolling to settle before asking for tiles. */
private const val TILE_REQUEST_DEBOUNCE_MS = 80L

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
        val density = LocalDensity.current

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

        LaunchedEffect(listState, zoomState, pageImages, pageSizes) {
            requestVisibleTiles(listState, zoomState, pageSizes, pageImages, density)
        }

        TrimMemoryEffect(pageImages)

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

/**
 * Keeps [pageImages] told which tiles are on screen. Nothing is requested
 * mid-pinch: the preview only transforms what's already drawn, and tiles for
 * an intermediate zoom would be thrown away a moment later.
 */
@OptIn(FlowPreview::class)
private suspend fun requestVisibleTiles(
    listState: LazyListState,
    zoomState: ZoomState,
    pageSizes: List<PageSize>,
    pageImages: PageImageSource,
    density: Density,
) {
    val fitGapPx = with(density) { PAGE_GAP.roundToPx() }
    snapshotFlow {
        if (zoomState.isPreviewing || zoomState.viewportWidth == 0) return@snapshotFlow null
        // Same rounding as LazyColumn applies to the zoomed contentPadding.
        val paddingPx = with(density) { (PAGE_GAP * zoomState.scale).roundToPx() }
        val layout = pageLayout(zoomState, paddingPx)
        val fitWidth = zoomState.viewportWidth - 2 * fitGapPx
        listState.layoutInfo.visiblePages(layout, zoomState.viewportWidth).flatMap { page ->
            TileGrid.forZoom(page.pageIndex, pageSizes[page.pageIndex], fitWidth, zoomState.scale)
                .tilesFor(page)
        }
    }
        .filterNotNull()
        .distinctUntilChanged()
        .debounce(TILE_REQUEST_DEBOUNCE_MS)
        .collect(pageImages::requestTiles)
}

@Composable
private fun TrimMemoryEffect(pageImages: PageImageSource) {
    val context = LocalContext.current
    DisposableEffect(context, pageImages) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) pageImages.trimMemory()
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = pageImages.trimMemory()
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
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
    var draft by remember(pageIndex, draftWidthPx) {
        mutableStateOf(pageImages.cachedDraft(pageIndex, draftWidthPx))
    }
    // Cancelled automatically when the item leaves the composition.
    LaunchedEffect(pageIndex, draftWidthPx) {
        if (draft == null) draft = pageImages.loadDraft(pageIndex, draftWidthPx)
    }
    val colorFilter = if (nightMode) NightModeFilter else null

    // Sized from the page's aspect ratio up front so the layout never jumps.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.aspectRatio)
            .background(if (nightMode) Color.Black else Color.White)
            // Tiles are read in the draw phase: a new tile only redraws this
            // page, it doesn't recompose it.
            .drawWithContent {
                drawContent()
                pageImages.renderedTiles(pageIndex).forEach { drawTile(it, colorFilter) }
            },
    ) {
        draft?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.reader_page_description, pageIndex + 1),
                contentScale = ContentScale.FillBounds,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Draws a tile scaled from its render resolution to the page's laid out size.
 * Edges are rounded independently so neighbouring tiles share an edge exactly
 * and no hairline gaps appear between them.
 */
private fun DrawScope.drawTile(rendered: RenderedTile, colorFilter: ColorFilter?) {
    val tile: Tile = rendered.tile
    val ratio = size.width / tile.key.renderWidth
    val left = (tile.bounds.left * ratio).roundToInt()
    val top = (tile.bounds.top * ratio).roundToInt()
    val right = (tile.bounds.right * ratio).roundToInt()
    val bottom = (tile.bounds.bottom * ratio).roundToInt()
    drawImage(
        image = rendered.image,
        srcSize = IntSize(tile.bounds.width, tile.bounds.height),
        dstOffset = IntOffset(left, top),
        dstSize = IntSize(right - left, bottom - top),
        colorFilter = colorFilter,
    )
}
