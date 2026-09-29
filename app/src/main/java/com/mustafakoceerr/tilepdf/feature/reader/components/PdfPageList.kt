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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.core.geometry.CoordinateMapper
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.core.render.RenderedTile
import com.mustafakoceerr.tilepdf.core.render.Tile
import com.mustafakoceerr.tilepdf.feature.reader.SelectionActions
import com.mustafakoceerr.tilepdf.feature.reader.SelectionUiState
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Drafts are rendered at a fraction of the screen width to keep them cheap. */
private const val DRAFT_SCALE = 0.5f

/** Space between pages and around the list, before zoom. */
internal val PAGE_GAP = 8.dp

private val HighlightColor = Color(0x66FFEB3B)
private val CurrentHighlightColor = Color(0x99FF9800)
private val SelectionColor = Color(0x553D7BFF)

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
internal fun PdfPageList(
    pageSizes: List<PageSize>,
    pageImages: PageImageSource,
    initialPosition: ReadingPosition,
    nightMode: Boolean,
    matches: List<SearchMatch>,
    currentMatch: SearchMatch?,
    onPositionChanged: (ReadingPosition) -> Unit,
    goToPageOpen: Boolean,
    onGoToPageOpenChange: (Boolean) -> Unit,
    pageJump: PageJump?,
    onPageJumpHandled: () -> Unit,
    selection: SelectionUiState?,
    selectionActions: SelectionActions,
    modifier: Modifier = Modifier,
) {
    val matchesByPage = remember(matches) { matches.groupBy { it.pageIndex } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val draftWidthPx = (constraints.maxWidth * DRAFT_SCALE).toInt()
        val density = LocalDensity.current
        val zoomState = rememberZoomState(
            initialScale = initialPosition.zoom,
            initialPanFraction = initialPosition.panFraction,
            initialViewportWidth = constraints.maxWidth,
        )
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialPosition.pageIndex,
            initialFirstVisibleItemScrollOffset = remember {
                initialScrollOffset(initialPosition, pageSizes, constraints.maxWidth, zoomState.scale, density)
            },
        )

        LaunchedEffect(listState, zoomState) {
            snapshotFlow {
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                ReadingPosition(
                    pageIndex = listState.firstVisibleItemIndex,
                    pageOffsetFraction = first?.let {
                        listState.firstVisibleItemScrollOffset.toFloat() / it.size
                    } ?: 0f,
                    zoom = zoomState.scale,
                    panFraction = zoomState.panFraction,
                )
            }.collect(onPositionChanged)
        }

        LaunchedEffect(listState, zoomState, pageImages, pageSizes) {
            requestVisibleTiles(listState, zoomState, pageSizes, pageImages, density)
        }

        TrimMemoryEffect(pageImages)

        LaunchedEffect(pageJump) {
            val jump = pageJump ?: return@LaunchedEffect
            val pageIndex = jump.pageIndex.coerceIn(0, pageSizes.lastIndex)
            scrollToPage(listState, zoomState, pageIndex, jump.topPt, pageSizes[pageIndex], density)
            onPageJumpHandled()
        }

        LaunchedEffect(currentMatch) {
            val match = currentMatch ?: return@LaunchedEffect
            scrollToMatch(listState, zoomState, match, pageSizes[match.pageIndex], density)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(
                    zoomState = zoomState,
                    listState = listState,
                    onTap = { selectionActions.onClear() },
                    onLongPress = { position ->
                        pagePointAt(position, listState, zoomState, pageSizes, density)
                            ?.let { (page, x, y) -> selectionActions.onLongPress(page, x, y) }
                    },
                ),
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
                    matches = matchesByPage[pageIndex].orEmpty(),
                    currentMatch = currentMatch,
                    selection = selection?.takeIf { it.pageIndex == pageIndex },
                    selectionActions = selectionActions,
                )
            }
        }

        PageNavigationOverlay(
            listState = listState,
            pageCount = pageSizes.size,
            onRequestGoToPage = { onGoToPageOpenChange(true) },
            modifier = Modifier.fillMaxSize(),
        )

        if (goToPageOpen) {
            val scope = rememberCoroutineScope()
            GoToPageDialog(
                pageCount = pageSizes.size,
                currentPage = listState.layoutInfo.centerPage() ?: 0,
                onGoToPage = { pageIndex ->
                    onGoToPageOpenChange(false)
                    scope.launch { listState.scrollToItem(pageIndex) }
                },
                onDismiss = { onGoToPageOpenChange(false) },
            )
        }
    }
}

@Composable
private fun TrimMemoryEffect(pageImages: PageImageSource) {
    val context = LocalContext.current
    DisposableEffect(context, pageImages) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // Since Android 14 only UI_HIDDEN and BACKGROUND are delivered:
                // drop off-screen caches once the reader isn't visible.
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) pageImages.trimMemory()
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
    matches: List<SearchMatch>,
    currentMatch: SearchMatch?,
    selection: SelectionUiState?,
    selectionActions: SelectionActions,
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
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Sized from the page's aspect ratio up front so the layout never jumps.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.aspectRatio)
            .onSizeChanged { pageWidthPx = it.width.toFloat() }
            .onGloballyPositioned { coordinates = it },
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(if (nightMode) Color.Black else Color.White)
                // Tiles are read in the draw phase: a new tile only redraws
                // this page, it doesn't recompose it.
                .drawWithContent {
                    drawContent()
                    pageImages.renderedTiles(pageIndex).forEach { drawTile(it, colorFilter) }
                    matches.forEach { match ->
                        val color = if (match == currentMatch) CurrentHighlightColor else HighlightColor
                        match.rects.forEach { drawHighlight(it, pageSize, color) }
                    }
                    selection?.rects?.forEach { drawHighlight(it, pageSize, SelectionColor) }
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
        // Siblings of the page layer, not its children: the page draws its
        // tiles after its children, which would cover the handles.
        if (selection != null && pageWidthPx > 0f) {
            SelectionHandles(selection, pageSize, pageWidthPx, selectionActions.onDragHandle)
            SelectionToolbarEffect(selection, pageSize, pageWidthPx, coordinates, selectionActions)
        }
    }
}

/**
 * Shows the system's floating text toolbar (Copy, Select all) above the
 * selection, positioned in root coordinates as Android expects.
 */
@Composable
private fun SelectionToolbarEffect(
    selection: SelectionUiState,
    pageSize: PageSize,
    pageWidthPx: Float,
    coordinates: LayoutCoordinates?,
    actions: SelectionActions,
) {
    val textToolbar = LocalTextToolbar.current
    val handleHeightPx = with(LocalDensity.current) { HandleTouchSize.toPx() }
    LaunchedEffect(selection, coordinates, pageWidthPx) {
        val page = coordinates?.takeIf { it.isAttached } ?: return@LaunchedEffect
        val rects = selection.rects.map { CoordinateMapper.toPagePixels(it, pageSize, pageWidthPx) }
        val topLeft = page.localToRoot(Offset(rects.minOf { it.left }, rects.minOf { it.top }))
        // Include the handles hanging below the text, so a toolbar placed
        // under the selection doesn't cover them.
        val bottomRight = page.localToRoot(
            Offset(rects.maxOf { it.right }, rects.maxOf { it.bottom } + handleHeightPx)
        )
        textToolbar.showMenu(
            rect = Rect(topLeft, bottomRight),
            onCopyRequested = actions.onCopy,
            onSelectAllRequested = actions.onSelectAll,
        )
    }
    DisposableEffect(textToolbar) { onDispose { textToolbar.hide() } }
}

private fun DrawScope.drawHighlight(
    rect: PdfRect,
    pageSize: PageSize,
    color: Color,
) {
    val px = CoordinateMapper.toPagePixels(rect, pageSize, size.width)
    drawRect(color, topLeft = Offset(px.left, px.top), size = Size(px.width, px.height))
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
