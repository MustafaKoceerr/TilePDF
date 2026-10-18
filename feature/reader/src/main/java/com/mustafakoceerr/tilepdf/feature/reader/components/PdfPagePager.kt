package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import com.mustafakoceerr.tilepdf.core.geometry.CoordinateMapper
import com.mustafakoceerr.tilepdf.core.geometry.Viewport
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.core.render.TileGrid
import com.mustafakoceerr.tilepdf.core.render.tilesFor
import com.mustafakoceerr.tilepdf.feature.reader.SelectionActions
import com.mustafakoceerr.tilepdf.feature.reader.SelectionUiState
import com.mustafakoceerr.tilepdf.feature.reader.annotation.PageDrawing
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/** Wait for a pinch or pan to settle before asking for tiles. */
private const val TILE_REQUEST_DEBOUNCE_MS = 80L

/**
 * One page at a time, swiped sideways. Each page fits the screen; only the
 * settled page zooms, and swiping is off while it is zoomed so a drag pans.
 */
@OptIn(FlowPreview::class)
@Composable
internal fun PdfPagePager(
    pageSizes: List<PageSize>,
    /** How each page is shown; [pageSizes] are their laid-out sizes. */
    pageTransforms: List<PageViewTransform>,
    pageImages: PageImageSource,
    initialPage: Int,
    pageTheme: PageTheme,
    matches: List<SearchMatch>,
    currentMatch: SearchMatch?,
    onPageChanged: (Int) -> Unit,
    goToPageOpen: Boolean,
    onGoToPageOpenChange: (Boolean) -> Unit,
    /** A page number was entered; the jump comes back as [pageJump]. */
    onGoToPage: (pageIndex: Int) -> Unit,
    pageJump: PageJump?,
    onPageJumpHandled: () -> Unit,
    onTapPage: (pageIndex: Int, x: Float, y: Float) -> Unit,
    onTapOutsidePage: () -> Unit,
    /** Room for the bars; the page indicator stays clear of it. */
    chromePadding: PaddingValues,
    selection: SelectionUiState?,
    selectionActions: SelectionActions,
    modifier: Modifier = Modifier,
    /** TilePDF's annotations by page. */
    annotations: Map<Int, List<Annotation>> = emptyMap(),
    /** Each page's pen or eraser input; null when neither is active. */
    drawingFor: ((pageIndex: Int) -> PageDrawing)? = null,
    /** Two pages side by side, as [spreads] pairs them. */
    twoPageSpreads: Boolean = false,
) {
    val matchesByPage = remember(matches) { matches.groupBy { it.pageIndex } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val viewport = IntSize(constraints.maxWidth, constraints.maxHeight)
        val draftWidthPx = (constraints.maxWidth * DRAFT_SCALE).toInt()
        // Each pager page: one PDF page, or a book spread of two.
        val groups = remember(pageSizes.size, twoPageSpreads) {
            if (twoPageSpreads) spreads(pageSizes.size) else List(pageSizes.size) { listOf(it) }
        }
        val pagerState = rememberPagerState(initialPage = spreadOf(initialPage, groups)) { groups.size }
        val settled = pagerState.settledPage.coerceIn(0, groups.lastIndex)
        // A new page, or a new screen size, starts unzoomed and fitted.
        val zooms = remember(settled, viewport, groups) { slotZooms(groups[settled], pageSizes, viewport) }

        LaunchedEffect(pagerState, groups) {
            // The reading position is the spread's left page.
            snapshotFlow { groups[pagerState.currentPage.coerceIn(0, groups.lastIndex)].first() }.collect(onPageChanged)
        }

        LaunchedEffect(zooms, pageImages) {
            val pages = groups[settled]
            snapshotFlow {
                // One request for the whole spread: a request replaces the one before.
                pages.zip(zooms).flatMap { (page, zoom) ->
                    val screen = Viewport(zoom.viewport.width, zoom.viewport.height)
                    val visible = screen.visiblePage(page, zoom.pageBounds()) ?: return@flatMap emptyList()
                    TileGrid.forZoom(page, pageSizes[page], zoom.fitted.width, zoom.scale).tilesFor(visible)
                }
            }
                .distinctUntilChanged()
                .debounce(TILE_REQUEST_DEBOUNCE_MS)
                .collect(pageImages::requestTiles)
        }

        TrimMemoryEffect(pageImages) { uiHidden -> pageImages.trimMemory(uiHidden) }

        LaunchedEffect(pageJump) {
            val jump = pageJump ?: return@LaunchedEffect
            pagerState.scrollToPage(spreadOf(jump.pageIndex, groups))
            onPageJumpHandled()
        }

        LaunchedEffect(currentMatch) {
            val match = currentMatch ?: return@LaunchedEffect
            val target = spreadOf(match.pageIndex, groups)
            if (target != pagerState.currentPage) pagerState.scrollToPage(target)
        }

        HorizontalPager(
            state = pagerState,
            key = { groups[it].first() },
            beyondViewportPageCount = 0,
            userScrollEnabled = zooms.all { it.scale == 1f } && drawingFor == null,
            modifier = Modifier.fillMaxSize(),
        ) { groupIndex ->
            val pages = groups[groupIndex]
            // Spreads sliding in or out stay fitted.
            val resting = remember(pages, viewport) { slotZooms(pages, pageSizes, viewport) }
            val slotZooms = if (groupIndex == settled) zooms else resting

            Row(Modifier.fillMaxSize()) {
                pages.zip(slotZooms).forEach { (pageIndex, pageZoom) ->
                    val transform = pageTransforms[pageIndex]
                    val fitted = pageZoom.fitted
                    Box(
                        Modifier
                            .width(with(LocalDensity.current) { pageZoom.viewport.width.toDp() })
                            .fillMaxHeight()
                            // A zoomed half stays in its half.
                            .clipToBounds()
                            .pageGestures(pageZoom, pageIndex, transform, onTapPage, onTapOutsidePage, selectionActions, drawingFor != null),
                    ) {
                        PdfPage(
                            pageIndex = pageIndex,
                            transform = transform,
                            draftWidthPx = draftWidthPx,
                            pageImages = pageImages,
                            pageTheme = pageTheme,
                            matches = matchesByPage[pageIndex].orEmpty(),
                            currentMatch = currentMatch,
                            selection = selection?.takeIf { it.pageIndex == pageIndex },
                            selectionActions = selectionActions,
                            annotations = annotations[pageIndex].orEmpty(),
                            drawing = drawingFor?.invoke(pageIndex),
                            modifier = Modifier
                                .offset { IntOffset(fitted.left, fitted.top) }
                                .graphicsLayer {
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    translationX = pageZoom.offset.x
                                    translationY = pageZoom.offset.y
                                    scaleX = pageZoom.scale
                                    scaleY = pageZoom.scale
                                }
                                // Exactly the fitted size, as the tile maths assume.
                                .layout { measurable, _ ->
                                    val placeable = measurable.measure(Constraints.fixed(fitted.width, fitted.height))
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                },
                        )
                    }
                }
            }
        }

        PagerPageIndicator(
            currentPage = groups.getOrNull(pagerState.currentPage)?.first() ?: 0,
            pageCount = pageSizes.size,
            scrolling = pagerState.isScrollInProgress,
            onClick = { onGoToPageOpenChange(true) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(chromePadding)
                .padding(bottom = 24.dp),
        )

        if (goToPageOpen) {
            GoToPageDialog(
                pageCount = pageSizes.size,
                currentPage = groups.getOrNull(pagerState.currentPage)?.first() ?: 0,
                onGoToPage = { pageIndex ->
                    onGoToPageOpenChange(false)
                    onGoToPage(pageIndex)
                },
                onDismiss = { onGoToPageOpenChange(false) },
            )
        }
    }
}

/**
 * A zoom per page of a pager page, each fitted into its share of the
 * [viewport]. A spread's pages meet in the middle, like a book's.
 */
private fun slotZooms(pages: List<Int>, pageSizes: List<PageSize>, viewport: IntSize): List<PageZoom> {
    val slot = IntSize(viewport.width / pages.size, viewport.height)
    return pages.mapIndexed { index, page ->
        val fitted = fitPage(pageSizes[page], slot)
        val atSpine = when {
            pages.size == 1 -> fitted
            index == 0 -> fitted.translate(slot.width - fitted.right, 0)
            else -> fitted.translate(-fitted.left, 0)
        }
        PageZoom(atSpine, slot)
    }
}

/**
 * Pinch, double tap and pan for [zoom], and taps reported in PDF points of
 * the page. Positions arrive in the page slot's coordinates, which are the
 * viewport's, so [PageZoom] maps them directly.
 */
private fun Modifier.pageGestures(
    zoom: PageZoom,
    pageIndex: Int,
    transform: PageViewTransform,
    onTapPage: (pageIndex: Int, x: Float, y: Float) -> Unit,
    onTapOutsidePage: () -> Unit,
    selectionActions: SelectionActions,
    /** Pen or eraser active: one finger draws, so it neither pans nor double-taps. */
    drawing: Boolean,
): Modifier {
    fun pdfPoint(position: Offset) = zoom.toPagePixels(position)?.let { (x, y) ->
        CoordinateMapper.toPdfPoints(x, y, transform, zoom.fitted.width.toFloat())
    }
    return this
        .pointerInput(zoom) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    // Initial pass: take two-finger gestures before the pager sees them.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        zoom.transform(event.calculateCentroid(useCurrent = true), event.calculatePan(), event.calculateZoom())
                        event.changes.fastForEach { it.consume() }
                    }
                } while (event.changes.fastAny { it.pressed })
            }
        }
        .pointerInput(zoom, pageIndex, drawing) {
            detectTapGestures(
                onDoubleTap = if (drawing) null else zoom::doubleTap,
                onTap = { position ->
                    pdfPoint(position)?.let { (x, y) -> onTapPage(pageIndex, x, y) } ?: onTapOutsidePage()
                },
                onLongPress = { position ->
                    pdfPoint(position)?.let { (x, y) -> selectionActions.onLongPress(pageIndex, x, y) }
                },
            )
        }
        // Zoomed, the pager doesn't swipe: one-finger drags pan instead.
        .pointerInput(zoom, zoom.scale > 1f, drawing) {
            if (zoom.scale <= 1f || drawing) return@pointerInput
            detectDragGestures { change, drag ->
                change.consume()
                zoom.panBy(drag)
            }
        }
}

/** "12 / 300", shown while swiping and for a moment after. */
@Composable
private fun PagerPageIndicator(
    currentPage: Int,
    pageCount: Int,
    scrolling: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(scrolling, currentPage) {
        visible = true
        if (!scrolling) {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        PageIndicator(currentPage + 1, pageCount, onClick = onClick)
    }
}
