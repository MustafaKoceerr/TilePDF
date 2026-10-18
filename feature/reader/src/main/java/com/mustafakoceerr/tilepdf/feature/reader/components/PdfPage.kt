package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.core.geometry.CoordinateMapper
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.core.render.RenderedTile
import com.mustafakoceerr.tilepdf.core.render.Tile
import com.mustafakoceerr.tilepdf.feature.reader.SelectionActions
import com.mustafakoceerr.tilepdf.feature.reader.SelectionUiState
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.feature.reader.annotation.Erased
import com.mustafakoceerr.tilepdf.feature.reader.annotation.PageDrawing
import com.mustafakoceerr.tilepdf.feature.reader.annotation.drawAnnotations
import com.mustafakoceerr.tilepdf.feature.reader.annotation.drawLiveStroke
import com.mustafakoceerr.tilepdf.feature.reader.annotation.drawingInput
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlin.math.roundToInt
/** Drafts are rendered at a fraction of the screen width to keep them cheap. */
internal const val DRAFT_SCALE = 0.5f

private val HighlightColor = Color(0x66FFEB3B)
private val CurrentHighlightColor = Color(0x99FF9800)
private val SelectionColor = Color(0x553D7BFF)
private val FormFieldColor = Color(0x262196F3)

/** One page: its draft, sharp tiles, search highlights and selection. */
@Composable
internal fun PdfPage(
    pageIndex: Int,
    /** How the page is shown; its size is the laid-out page's. */
    transform: PageViewTransform,
    draftWidthPx: Int,
    pageImages: PageImageSource,
    pageTheme: PageTheme,
    matches: List<SearchMatch>,
    currentMatch: SearchMatch?,
    selection: SelectionUiState?,
    selectionActions: SelectionActions,
    modifier: Modifier = Modifier,
    /** TilePDF's annotations on this page; PDFium draws everyone else's. */
    annotations: List<Annotation> = emptyList(),
    /** Set while the pen or eraser is active: one-finger drags draw or erase. */
    drawing: PageDrawing? = null,
    /** Fillable form widgets on this page, tinted so they can be found. */
    formFields: List<FormField> = emptyList(),
) {
    // The stroke being drawn and the strokes being erased stay here until the
    // finger lifts, so drawing redraws this page only, never the screen.
    var liveStroke by remember { mutableStateOf<List<PdfPoint>>(emptyList()) }
    var pendingErase by remember { mutableStateOf(Erased.None) }
    // Cleared once the finished stroke arrives as an annotation: no gap in between.
    LaunchedEffect(annotations) { liveStroke = emptyList() }
    val currentDrawing by rememberUpdatedState(drawing)
    val currentAnnotations by rememberUpdatedState(annotations)
    // Bumped when the page's content changed and must be rendered again.
    val version = pageImages.version(pageIndex)
    // Seed from the cache so pages scrolled back into view don't flash blank.
    var draft by remember(pageIndex, draftWidthPx, version) {
        mutableStateOf(pageImages.cachedDraft(pageIndex, draftWidthPx))
    }
    // Cancelled automatically when the item leaves the composition.
    LaunchedEffect(pageIndex, draftWidthPx, version) {
        if (draft == null) draft = pageImages.loadDraft(pageIndex, draftWidthPx)
    }
    val colorFilter = remember(pageTheme) { pageTheme.colorFilter() }
    val pageSize = transform.size
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Sized from the page's aspect ratio up front so the layout never jumps.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.aspectRatio)
            .onSizeChanged { pageWidthPx = it.width.toFloat() }
            .onGloballyPositioned { coordinates = it }
            .then(
                if (drawing == null) {
                    Modifier
                } else {
                    Modifier.drawingInput(
                        tool = drawing.tool,
                        pageIndex = pageIndex,
                        drawing = { currentDrawing ?: drawing },
                        own = { currentAnnotations },
                        toPoint = { position ->
                            CoordinateMapper.toPdfPoints(position.x, position.y, transform, pageWidthPx)
                        },
                        onLiveStroke = { liveStroke = it },
                        onLiveErase = { pendingErase = it },
                    )
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(pageTheme.paperColor)
                // Tiles are read in the draw phase: a new tile only redraws
                // this page, it doesn't recompose it.
                .drawWithContent {
                    drawContent()
                    pageImages.renderedTiles(pageIndex).forEach { drawTile(it, colorFilter) }
                    formFields.forEach { if (!it.readOnly) drawHighlight(it.rect, transform, FormFieldColor) }
                    matches.forEach { match ->
                        val color = if (match == currentMatch) CurrentHighlightColor else HighlightColor
                        match.rects.forEach { drawHighlight(it, transform, color) }
                    }
                    val erasing = pendingErase.ids
                    // In page points, through the page's turn and crop: underlines stay under their text.
                    withTransform({ transform(transform.drawMatrix(size.width / pageSize.widthPt)) }) {
                        drawAnnotations(
                            if (erasing.isEmpty()) annotations else annotations.filterNot { it.id in erasing },
                            scale = 1f,
                            theme = pageTheme,
                        )
                        val pen = drawing
                        if (pen != null && liveStroke.isNotEmpty()) {
                            drawLiveStroke(liveStroke, pen.penColor, pen.penWidthPt, scale = 1f, theme = pageTheme)
                        }
                    }
                    selection?.rects?.forEach { drawHighlight(it, transform, SelectionColor) }
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
            SelectionHandles(selection, transform, pageWidthPx, selectionActions.onDragHandle)
            SelectionToolbarEffect(selection, transform, pageWidthPx, coordinates, selectionActions)
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
    transform: PageViewTransform,
    pageWidthPx: Float,
    coordinates: LayoutCoordinates?,
    actions: SelectionActions,
) {
    val textToolbar = LocalTextToolbar.current
    val handleHeightPx = with(LocalDensity.current) { HandleTouchSize.toPx() }
    LaunchedEffect(selection, coordinates, pageWidthPx) {
        val page = coordinates?.takeIf { it.isAttached } ?: return@LaunchedEffect
        val rects = selection.rects.map { CoordinateMapper.toPagePixels(it, transform, pageWidthPx) }
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
    transform: PageViewTransform,
    color: Color,
) {
    val px = CoordinateMapper.toPagePixels(rect, transform, size.width)
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
