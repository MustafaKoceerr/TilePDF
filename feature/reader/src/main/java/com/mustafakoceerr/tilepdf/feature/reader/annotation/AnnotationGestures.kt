package com.mustafakoceerr.tilepdf.feature.reader.annotation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation

/** What a page needs to let the pen draw or the eraser erase on it. */
internal class PageDrawing(
    val tool: AnnotationTool,
    val penColor: Int,
    val penWidthPt: Float,
    /** Other apps' annotations on the page, for the eraser. */
    val foreign: List<ForeignAnnotation>,
    val onStroke: (List<PdfPoint>) -> Unit,
    val onErase: (Erased) -> Unit,
)

/**
 * One-finger drags draw (pen) or erase (eraser) on a page. Positions arrive
 * in the page's own layout pixels and [toPoint] maps them to page points,
 * so zoom and pan don't matter. A second finger cancels the gesture: two
 * fingers belong to pinch and pan.
 *
 * @param drawing the page's current [PageDrawing]; read at each event, so
 *   new callbacks or annotations don't restart a gesture in progress.
 * @param onLiveStroke the stroke so far, as it grows.
 * @param onLiveErase the annotations erased so far, as they add up.
 */
internal fun Modifier.drawingInput(
    tool: AnnotationTool,
    pageIndex: Int,
    drawing: () -> PageDrawing,
    own: () -> List<Annotation>,
    toPoint: (Offset) -> PdfPoint,
    onLiveStroke: (List<PdfPoint>) -> Unit,
    onLiveErase: (Erased) -> Unit,
): Modifier = pointerInput(tool, pageIndex) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val stroke = StrokeBuilder()
        var erased = Erased.None
        var last = toPoint(down.position)
        when (tool) {
            AnnotationTool.Pen -> {
                stroke.add(last)
                onLiveStroke(stroke.points.toList())
            }
            AnnotationTool.Eraser -> {
                erased = erasedBy(own(), drawing().foreign, pageIndex, last, last)
                onLiveErase(erased)
            }
            else -> return@awaitEachGesture
        }
        down.consume()
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || event.changes.count { it.pressed } > 1 || change.isConsumed) {
                cancelled = true
                break
            }
            if (!change.pressed) break
            val point = toPoint(change.position)
            when (tool) {
                AnnotationTool.Pen -> if (stroke.add(point)) onLiveStroke(stroke.points.toList())
                else -> {
                    erased += erasedBy(own(), drawing().foreign, pageIndex, last, point)
                    onLiveErase(erased)
                }
            }
            last = point
            change.consume()
        }
        when {
            cancelled -> {
                onLiveStroke(emptyList())
                onLiveErase(Erased.None)
            }
            tool == AnnotationTool.Pen -> drawing().onStroke(stroke.points.toList())
            else -> {
                if (!erased.isEmpty) drawing().onErase(erased)
                onLiveErase(Erased.None)
            }
        }
    }
}
