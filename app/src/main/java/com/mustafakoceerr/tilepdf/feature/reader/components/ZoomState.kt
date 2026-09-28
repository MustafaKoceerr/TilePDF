package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlin.math.abs
import kotlin.math.roundToInt

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * Document-level zoom for the page list.
 *
 * Zooming happens in two phases:
 * - **Preview** — while fingers are down, the already laid out content is
 *   transformed with a cheap `graphicsLayer` ([previewZoom], [previewOffset]).
 *   Nothing is re-measured or re-rendered, so pinching stays smooth.
 * - **Commit** — when the gesture ends, [scale] is updated so pages are laid
 *   out at their new size, and the scroll position is corrected so the content
 *   under the viewport's top-left corner stays where the preview showed it.
 *
 * All positions are in viewport pixels. The preview maps a viewport point `p`
 * to `p * previewZoom + previewOffset` (transform origin at the top-left).
 */
class ZoomState(
    private val flingDecay: DecayAnimationSpec<Float>,
    initialScale: Float = MIN_SCALE,
) {

    /** Committed zoom; the content is laid out at `viewportWidth * scale`. */
    var scale by mutableFloatStateOf(initialScale.coerceIn(MIN_SCALE, MAX_SCALE))
        private set

    /** Horizontal scroll of the zoomed content, in `0..maxPanX`. */
    var panX by mutableFloatStateOf(0f)
        private set

    var previewZoom by mutableFloatStateOf(1f)
        private set

    var previewOffset by mutableStateOf(Offset.Zero)
        private set

    /** True mid-gesture, while the content is only transformed, not laid out. */
    val isPreviewing: Boolean get() = previewZoom != 1f || previewOffset != Offset.Zero

    /** Updated from layout; not observable state since only gestures read it. */
    var viewportWidth = 0
        internal set

    private val maxPanX: Float get() = viewportWidth * (scale - 1f)

    /** Applies one frame of a pinch gesture around [centroid]. */
    fun onTransform(centroid: Offset, pan: Offset, zoom: Float) {
        val newZoom = (scale * previewZoom * zoom).coerceIn(MIN_SCALE, MAX_SCALE) / scale
        val step = newZoom / previewZoom
        // Keep the content under the centroid fixed, then follow the fingers.
        previewOffset = (previewOffset - centroid) * step + centroid + pan
        previewZoom = newZoom
    }

    /** Horizontal drags pan the zoomed content. */
    val draggableState = DraggableState { delta -> panBy(delta) }

    /**
     * Makes the preview permanent. The returned [ZoomCommit] tells the caller
     * how to fix the vertical scroll position, which this class doesn't own.
     */
    fun commit(): ZoomCommit {
        val zoom = previewZoom
        val anchorY = -previewOffset.y / zoom
        // The viewport x shown at the left edge was `-offset.x / zoom`, i.e.
        // content x `panX - offset.x / zoom`; after scaling by `zoom` that
        // content point must sit at viewport x = 0.
        val newPanX = zoom * panX - previewOffset.x
        scale *= zoom
        panX = newPanX.coerceIn(0f, maxPanX)
        previewZoom = 1f
        previewOffset = Offset.Zero
        return ZoomCommit(zoom, anchorY)
    }

    /** Zooms to [target] around [focus] as if pinched there, then commits. */
    fun zoomTo(target: Float, focus: Offset): ZoomCommit {
        previewZoom = target.coerceIn(MIN_SCALE, MAX_SCALE) / scale
        previewOffset = focus * (1f - previewZoom)
        return commit()
    }

    /** Scrolls horizontally so content x [x] is at the viewport's left edge. */
    fun panTo(x: Float) {
        panX = x.coerceIn(0f, maxPanX)
    }

    /** Scrolls horizontally by a drag delta; returns the delta actually consumed. */
    fun panBy(delta: Float): Float {
        val old = panX
        panX = (panX - delta).coerceIn(0f, maxPanX)
        return old - panX
    }

    suspend fun fling(velocity: Float) {
        var last = 0f
        AnimationState(initialValue = 0f, initialVelocity = velocity).animateDecay(flingDecay) {
            val delta = value - last
            last = value
            // Stop once an edge clamps the movement instead of spinning uselessly.
            if (abs(panBy(delta) - delta) > 0.5f) cancelAnimation()
        }
    }

    val doubleTapTarget: Float get() = if (scale > MIN_SCALE + 0.01f) MIN_SCALE else DOUBLE_TAP_SCALE
}

/**
 * Result of a zoom commit: the content grew by [zoom], and the content that was
 * at viewport y [anchorY] (pre-commit layout) should move to the viewport top.
 */
data class ZoomCommit(val zoom: Float, val anchorY: Float)

@Composable
fun rememberZoomState(initialScale: Float = MIN_SCALE): ZoomState {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(decay) { ZoomState(decay, initialScale) }
}

/**
 * Makes a vertical lazy list zoomable. Apply it as the list's modifier: the
 * list is laid out `scale` times wider than the viewport and shifted by `panX`;
 * pinch, double tap and horizontal pan are handled here while vertical scroll
 * stays with the list itself.
 */
fun Modifier.zoomable(zoomState: ZoomState, listState: LazyListState): Modifier = this
    .clipToBounds()
    .onSizeChanged { zoomState.viewportWidth = it.width }
    .pointerInput(zoomState) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoomed = false
            do {
                // Initial pass: see multi-touch before the list does, and
                // consume it so the list doesn't scroll during a pinch.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    zoomState.onTransform(
                        centroid = event.calculateCentroid(useCurrent = true),
                        pan = event.calculatePan(),
                        zoom = event.calculateZoom(),
                    )
                    event.changes.fastForEach { it.consume() }
                    zoomed = true
                }
            } while (event.changes.fastAny { it.pressed })
            if (zoomed) listState.keepAnchor(zoomState.commit())
        }
    }
    .pointerInput(zoomState) {
        detectTapGestures(onDoubleTap = { focus ->
            listState.keepAnchor(zoomState.zoomTo(zoomState.doubleTapTarget, focus))
        })
    }
    .draggable(
        state = zoomState.draggableState,
        orientation = Orientation.Horizontal,
        // At 1x there is nothing to pan; leave horizontal drags alone.
        enabled = zoomState.scale > 1f,
        onDragStopped = { velocity -> zoomState.fling(velocity) },
    )
    .graphicsLayer {
        scaleX = zoomState.previewZoom
        scaleY = zoomState.previewZoom
        translationX = zoomState.previewOffset.x
        translationY = zoomState.previewOffset.y
        transformOrigin = TransformOrigin(0f, 0f)
    }
    .layout { measurable, constraints ->
        val width = (constraints.maxWidth * zoomState.scale).roundToInt()
        val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeable.place(-zoomState.panX.roundToInt(), 0)
        }
    }

/**
 * Applies the vertical half of a [ZoomCommit]. Item heights scale with the zoom
 * (pages keep their aspect ratio); fixed spacing between items causes
 * negligible drift. Uses [LazyListState.requestScrollToItem] so the scroll fix
 * lands in the same frame as the new scale — no visible jump.
 */
private fun LazyListState.keepAnchor(commit: ZoomCommit) {
    val (zoom, anchorY) = commit
    val info = layoutInfo
    // Item offsets are measured from the end of the top content padding.
    val y = anchorY + info.viewportStartOffset
    val item = info.visibleItemsInfo.firstOrNull { it.offset + it.size > y } ?: return
    val fraction = ((y - item.offset) / item.size).coerceIn(0f, 1f)
    requestScrollToItem(
        index = item.index,
        scrollOffset = (fraction * item.size * zoom - info.viewportStartOffset).roundToInt(),
    )
}
