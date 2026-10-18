package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.geometry.CoordinateMapper
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.feature.reader.SelectionHandle
import com.mustafakoceerr.tilepdf.feature.reader.SelectionUiState
import kotlin.math.roundToInt

internal val HandleTouchSize = 40.dp
private val HandleRadius = 10.dp

/**
 * The two draggable selection handles, positioned in the page's pixel space.
 * Each reports the point it is dragged to in PDF points, measured at the
 * middle of the line it started on so dragging along a line stays on it.
 */
@Composable
internal fun SelectionHandles(
    selection: SelectionUiState,
    pageSize: PageSize,
    pageWidthPx: Float,
    onDrag: (SelectionHandle, x: Float, y: Float) -> Unit,
) {
    for (handle in SelectionHandle.entries) {
        val box = if (handle == SelectionHandle.Start) selection.startBox else selection.endBox
        val px = CoordinateMapper.toPagePixels(box, pageSize, pageWidthPx)
        Handle(
            handle = handle,
            // Handles hang from the bottom corner of the first / last character.
            anchor = Offset(if (handle == SelectionHandle.Start) px.left else px.right, px.bottom),
            lineCenter = Offset(if (handle == SelectionHandle.Start) px.left else px.right, (px.top + px.bottom) / 2),
            onDragTo = { point ->
                val (x, y) = CoordinateMapper.toPdfPoints(point.x, point.y, pageSize, pageWidthPx)
                onDrag(handle, x, y)
            },
        )
    }
}

@Composable
private fun Handle(
    handle: SelectionHandle,
    anchor: Offset,
    lineCenter: Offset,
    onDragTo: (Offset) -> Unit,
) {
    val touchPx = with(LocalDensity.current) { HandleTouchSize.toPx() }
    val color = MaterialTheme.colorScheme.primary
    // Read at drag start, while pointerInput keeps running across updates.
    val currentLineCenter by rememberUpdatedState(lineCenter)
    val currentOnDragTo by rememberUpdatedState(onDragTo)

    Box(
        modifier = Modifier
            .offset {
                // The handle's tip, at the top centre of its touch area, sits on the anchor.
                IntOffset((anchor.x - touchPx / 2).roundToInt(), anchor.y.roundToInt())
            }
            .size(HandleTouchSize)
            .pointerInput(handle) {
                var point = Offset.Zero
                detectDragGestures(
                    onDragStart = { point = currentLineCenter },
                ) { change, dragAmount ->
                    change.consume()
                    point += dragAmount
                    currentOnDragTo(point)
                }
            },
    ) {
        Canvas(Modifier.size(HandleTouchSize)) { drawTeardrop(handle, color, HandleRadius.toPx()) }
    }
}

/**
 * Material-style handle: a circle with one square corner touching the text,
 * pointing left for the start handle and right for the end handle.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTeardrop(
    handle: SelectionHandle,
    color: Color,
    radius: Float,
) {
    val tip = Offset(size.width / 2, 0f)
    val center = if (handle == SelectionHandle.Start) tip + Offset(-radius, radius) else tip + Offset(radius, radius)
    drawCircle(color, radius, center)
    val corner = if (handle == SelectionHandle.Start) Offset(center.x, tip.y) else tip
    drawRect(color, topLeft = corner, size = Size(radius, radius))
}
