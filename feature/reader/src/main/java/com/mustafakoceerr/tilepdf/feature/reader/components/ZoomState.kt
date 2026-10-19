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
 *   under the fingers stays where the preview showed it.
 *
 * All positions are in viewport pixels. The preview maps a viewport point `p`
 * to `p * previewZoom + previewOffset` (transform origin at the top-left).
 */
class ZoomState(
    private val flingDecay: DecayAnimationSpec<Float>,
    initialScale: Float = MIN_SCALE,
    initialPanFraction: Float = 0f,
    /** When known up front, lets the saved pan apply before the first frame. */
    initialViewportWidth: Int = 0,
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

    /** The last pinch centroid or double tap: the commit keeps the content there. */
    private var focus = Offset.Zero

    /** Applied once the viewport width, and so the pan range, is known. */
    private var pendingPanFraction: Float? = initialPanFraction.takeIf { it > 0f }

    /** Updated from layout; not observable state since only gestures read it. */
    var viewportWidth = 0
        internal set(value) {
            field = value
            pendingPanFraction?.let { fraction ->
                if (value > 0) {
                    pendingPanFraction = null
                    panX = fraction.coerceIn(0f, 1f) * maxPanX
                }
            }
        }

    init {
        viewportWidth = initialViewportWidth
    }

    /** [panX] as a fraction of the pan range; 0 when there's nothing to pan. */
    val panFraction: Float get() = if (maxPanX > 0f) panX / maxPanX else 0f

    private val maxPanX: Float get() = viewportWidth * (scale - 1f)

    /**
     * Applies one frame of a pinch gesture: [centroid] is where the fingers
     * are now, [pan] how far they moved since the last frame.
     */
    fun onTransform(centroid: Offset, pan: Offset, zoom: Float) {
        val newZoom = (scale * previewZoom * zoom).coerceIn(MIN_SCALE, MAX_SCALE) / scale
        val step = newZoom / previewZoom
        // The content that was under the fingers moves with them.
        val previousCentroid = centroid - pan
        val offset = centroid - (previousCentroid - previewOffset) * step
        previewOffset = Offset(clampPreviewX(offset.x, newZoom), offset.y)
        previewZoom = newZoom
        focus = centroid
    }

    /**
     * Keeps the zoomed content across the whole width, as the commit will
     * lay it out; otherwise the content would jump sideways on release.
     */
    private fun clampPreviewX(offsetX: Float, zoom: Float): Float {
        // The laid out content spans viewport x -panX..(width * scale - panX).
        val showsLeftEdge = panX * zoom
        val showsRightEdge = viewportWidth - (viewportWidth * scale - panX) * zoom
        // At exactly 1x both are the same; float error mustn't make coerceIn throw.
        return maxOf(showsRightEdge, minOf(offsetX, showsLeftEdge))
    }

    /** Horizontal drags pan the zoomed content. */
    val draggableState = DraggableState { delta -> panBy(delta) }

    /**
     * Makes the preview permanent. The returned [ZoomCommit] tells the caller
     * how to fix the vertical scroll position, which this class doesn't own.
     */
    fun commit(): ZoomCommit {
        val zoom = previewZoom
        // The content the preview shows under the fingers.
        val contentY = (focus.y - previewOffset.y) / zoom
        // The viewport x shown at the left edge was `-offset.x / zoom`, i.e.
        // content x `panX - offset.x / zoom`; after scaling by `zoom` that
        // content point must sit at viewport x = 0.
        val newPanX = zoom * panX - previewOffset.x
        // Float products can land just outside the range, e.g. 0.99999994.
        scale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        panX = newPanX.coerceIn(0f, maxPanX)
        previewZoom = 1f
        previewOffset = Offset.Zero
        return ZoomCommit(zoom, focus.y, contentY)
    }

    /** Zooms to [target] around [focus] as if pinched there, then commits. */
    fun zoomTo(target: Float, focus: Offset): ZoomCommit {
        previewZoom = target.coerceIn(MIN_SCALE, MAX_SCALE) / scale
        previewOffset = focus * (1f - previewZoom)
        this.focus = focus
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
 * at viewport y [contentY] (pre-commit layout) should move to viewport y [focusY].
 */
data class ZoomCommit(val zoom: Float, val focusY: Float, val contentY: Float)

/** A laid out list item: [offset] from the list's start, [size] long. */
internal data class ListItemBounds(val index: Int, val offset: Int, val size: Int)

/** Arguments for [LazyListState.requestScrollToItem]. */
internal data class ScrollTarget(val index: Int, val scrollOffset: Int)

/**
 * Where to scroll after [commit] so the anchored content lands at its focus.
 * Item heights scale with the zoom (pages keep their aspect ratio); fixed
 * spacing between items causes negligible drift. An anchor between items
 * holds on to the next one, one past the last to its bottom.
 */
internal fun anchoredScroll(items: List<ListItemBounds>, viewportStartOffset: Int, commit: ZoomCommit): ScrollTarget? {
    // Item offsets are measured from the end of the top content padding.
    val y = commit.contentY + viewportStartOffset
    val item = items.firstOrNull { it.offset + it.size > y } ?: items.lastOrNull() ?: return null
    val fraction = ((y - item.offset) / item.size).coerceIn(0f, 1f)
    // The item's top must sit `fraction` of its new size above the focus.
    val scrollOffset = fraction * item.size * commit.zoom - commit.focusY - viewportStartOffset
    return ScrollTarget(item.index, scrollOffset.roundToInt())
}

@Composable
fun rememberZoomState(
    initialScale: Float = MIN_SCALE,
    initialPanFraction: Float = 0f,
    initialViewportWidth: Int = 0,
): ZoomState {
    val decay = rememberSplineBasedDecay<Float>()
    return remember(decay) { ZoomState(decay, initialScale, initialPanFraction, initialViewportWidth) }
}

/**
 * Makes a vertical lazy list zoomable. Apply it as the list's modifier: the
 * list is laid out `scale` times wider than the viewport and shifted by `panX`;
 * pinch, double tap and horizontal pan are handled here while vertical scroll
 * stays with the list itself.
 */
fun Modifier.zoomable(
    zoomState: ZoomState,
    listState: LazyListState,
    onTap: (Offset) -> Unit = {},
    onLongPress: (Offset) -> Unit = {},
    /** Pen or eraser active: one finger draws, so it neither pans nor double-taps. */
    drawing: Boolean = false,
): Modifier = this
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
    .pointerInput(zoomState, drawing) {
        // One detector for every tap kind: separate ones would compete, and
        // the first to see a tap would starve the others (e.g. double tap).
        detectTapGestures(
            onDoubleTap = if (drawing) null else { focus ->
                listState.keepAnchor(zoomState.zoomTo(zoomState.doubleTapTarget, focus))
            },
            onTap = { onTap(it) },
            onLongPress = { onLongPress(it) },
        )
    }
    .draggable(
        state = zoomState.draggableState,
        orientation = Orientation.Horizontal,
        // At 1x there is nothing to pan; leave horizontal drags alone.
        enabled = zoomState.scale > 1f && !drawing,
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
 * Applies the vertical half of a [ZoomCommit]. Uses
 * [LazyListState.requestScrollToItem] so the scroll fix lands in the same
 * frame as the new scale — no visible jump.
 */
private fun LazyListState.keepAnchor(commit: ZoomCommit) {
    val info = layoutInfo
    val items = info.visibleItemsInfo.map { ListItemBounds(it.index, it.offset, it.size) }
    val target = anchoredScroll(items, info.viewportStartOffset, commit) ?: return
    requestScrollToItem(target.index, target.scrollOffset)
}
