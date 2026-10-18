package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.geometry.CoordinateMapper
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PageLayout
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.core.render.TileGrid
import com.mustafakoceerr.tilepdf.core.render.tilesFor
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

/*
 * Geometry and scrolling for the zoomable page list: where pages are, which
 * tiles are visible, and how to scroll to a page, position or match.
 */

/** Wait for scrolling to settle before asking for tiles. */
private const val TILE_REQUEST_DEBOUNCE_MS = 80L

/** Leaves a little room above a jump target such as a chapter heading. */
private val JUMP_MARGIN = 16.dp

/**
 * Page placement at the current zoom. The zoomed padding is rounded exactly
 * as LazyColumn rounds its contentPadding, so tiles, highlights and touch
 * mapping all agree with the laid-out pages to the pixel.
 */
internal fun zoomedPageLayout(zoomState: ZoomState, density: Density): PageLayout {
    val paddingPx = with(density) { (PAGE_GAP * zoomState.scale).roundToPx() }
    return pageLayout(zoomState, paddingPx)
}

/**
 * Pixel offset into the saved page, computed before the first layout. The
 * page height is derived exactly as the list will lay it out: list width
 * `round(W * zoom)`, minus the zoomed horizontal padding, at the page's
 * aspect ratio.
 */
internal fun initialScrollOffset(
    position: ReadingPosition,
    pageSizes: List<PageSize>,
    viewportWidth: Int,
    zoom: Float,
    density: Density,
): Int {
    val pageSize = pageSizes.getOrNull(position.pageIndex) ?: return 0
    val paddingPx = with(density) { (PAGE_GAP * zoom).roundToPx() }
    val pageWidth = (viewportWidth * zoom).roundToInt() - 2 * paddingPx
    return (position.pageOffsetFraction * pageWidth / pageSize.aspectRatio).roundToInt()
}

/**
 * Keeps [pageImages] told which tiles are on screen. Nothing is requested
 * mid-pinch: the preview only transforms what's already drawn, and tiles for
 * an intermediate zoom would be thrown away a moment later.
 */
@OptIn(FlowPreview::class)
internal suspend fun requestVisibleTiles(
    listState: LazyListState,
    zoomState: ZoomState,
    pageSizes: List<PageSize>,
    pageImages: PageImageSource,
    density: Density,
) {
    val fitGapPx = with(density) { PAGE_GAP.roundToPx() }
    snapshotFlow {
        if (zoomState.isPreviewing || zoomState.viewportWidth == 0) return@snapshotFlow null
        val layout = zoomedPageLayout(zoomState, density)
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

/**
 * The page under a viewport position and the position on it in PDF points,
 * or `null` over the gaps between pages.
 */
internal fun pagePointAt(
    position: Offset,
    listState: LazyListState,
    zoomState: ZoomState,
    pageTransforms: List<PageViewTransform>,
    density: Density,
): Triple<Int, Float, Float>? {
    val layout = zoomedPageLayout(zoomState, density)
    val info = listState.layoutInfo
    for (item in info.visibleItemsInfo) {
        val top = item.offset - info.viewportStartOffset
        val x = position.x - layout.pageLeft
        val y = position.y - top
        if (x in 0f..layout.pageWidth.toFloat() && y in 0f..item.size.toFloat()) {
            val (xPt, yPt) = CoordinateMapper.toPdfPoints(x, y, pageTransforms[item.index], layout.pageWidth.toFloat())
            return Triple(item.index, xPt, yPt)
        }
    }
    return null
}


/**
 * Scrolls so the page starts at the top of the screen; with a top point, so
 * that point on the page does (a little below the top); with an offset
 * fraction, exactly that far into the page. Uses the renderer's
 * points-to-pixels factor for the page's current laid-out width, like search
 * highlights do.
 */
internal suspend fun scrollToPage(
    listState: LazyListState,
    zoomState: ZoomState,
    jump: PageJump,
    transform: PageViewTransform,
    density: Density,
) {
    val pageWidth = zoomedPageLayout(zoomState, density).pageWidth
    val pageSize = transform.size
    val fraction = jump.offsetFraction
    val topPt = jump.topPt
    val offset = when {
        fraction != null -> (fraction * pageSize.heightForWidth(pageWidth)).roundToInt()
        topPt != null -> {
            // Where the point's line shows on the page as turned: a quarter turn puts it at the side.
            val viewTop = transform.toView(PdfPoint(transform.crop.left, topPt)).y.coerceAtLeast(0f)
            val marginPx = with(density) { JUMP_MARGIN.roundToPx() }
            (viewTop * pageSize.scaleForWidth(pageWidth) - marginPx).roundToInt().coerceAtLeast(0)
        }
        else -> 0
    }
    listState.scrollToItem(jump.pageIndex, offset)
}

/**
 * Whether a span from viewport y [top], [height] tall, is clear of the bars
 * drawn over the list's top and bottom content padding.
 */
internal fun LazyListLayoutInfo.isUnobscured(top: Float, height: Float): Boolean =
    top >= beforeContentPadding && top + height <= viewportSize.height - afterContentPadding

/**
 * Brings [match] into view, a third of the way down the screen, unless it is
 * already fully visible. When zoomed, also pans it horizontally into the centre.
 */
internal suspend fun scrollToMatch(
    listState: LazyListState,
    zoomState: ZoomState,
    match: SearchMatch,
    transform: PageViewTransform,
    density: Density,
) {
    val layout = zoomedPageLayout(zoomState, density)
    val rect = CoordinateMapper.toPagePixels(match.rects.first(), transform, layout.pageWidth.toFloat())
    val info = listState.layoutInfo
    val viewportHeight = info.viewportSize.height
    val item = info.visibleItemsInfo.firstOrNull { it.index == match.pageIndex }

    if (item != null) {
        val top = item.offset - info.viewportStartOffset + rect.top
        val left = layout.pageLeft + rect.left
        val fullyVisible = info.isUnobscured(top, rect.height) &&
            left >= 0 && left + rect.width <= zoomState.viewportWidth
        if (fullyVisible) return
        listState.animateScrollBy(top - viewportHeight / 3f)
    } else {
        // Far away: jump to the page, then settle on the match.
        listState.scrollToItem(match.pageIndex)
        listState.animateScrollBy(rect.top - viewportHeight / 3f)
    }
    if (zoomState.scale > 1f) {
        // Content x of the match centre: the page's left edge plus the pan.
        zoomState.panTo(layout.pageLeft + zoomState.panX + rect.centerX - zoomState.viewportWidth / 2f)
    }
}
