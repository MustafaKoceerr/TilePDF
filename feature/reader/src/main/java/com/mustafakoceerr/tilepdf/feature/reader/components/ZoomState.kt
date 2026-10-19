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

/** A two-finger drag changes the finger distance a little; that isn't pinching past a limit. */
private const val LIMIT_SLOP = 0.002f

/** Scales this close to a limit are the limit: float products miss it, e.g. 0.99999994. */
private const val LIMIT_SNAP = 0.0001f

/**
 * Document-level zoom for the page list.
 *
 * A pinch lays the pages out at the new [scale] on every frame, as other
 * readers do, so pages coming into view while zooming out are drawn at once
 * (their drafts, until sharp tiles follow) rather than appearing on release.
 * The horizontal scroll ([panX]) lives here; each step returns a [ZoomCommit]
 * telling the caller how to fix the list's vertical scroll position.
 *
 * All positions are in viewport pixels.
 */
class ZoomState(
    private val flingDecay: DecayAnimationSpec<Float>,
    initialScale: Float = MIN_SCALE,
    initialPanFraction: Float = 0f,
    /** When known up front, lets the saved pan apply before the first frame. */
    initialViewportWidth: Int = 0,
) {

    /** The content is laid out at `viewportWidth * scale`. */
    var scale by mutableFloatStateOf(initialScale.coerceIn(MIN_SCALE, MAX_SCALE))
        private set

    /** Horizontal scroll of the zoomed content, in `0..maxPanX`. */
    var panX by mutableFloatStateOf(0f)
        private set

    /**
     * From a pinch's first frame to its end. The list must not scroll
     * meanwhile: its own fling on release would replace the pinch's scroll
     * fix with the old layout's position, at the new page sizes.
     */
    var isPinching by mutableStateOf(false)
        private set

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
     * Applies one frame of a pinch: [centroid] is where the fingers are now,
     * [pan] how far they moved since the last frame. Returns how to scroll
     * the list so the content under the fingers stays under them, or null
     * when nothing moved.
     */
    fun onTransform(centroid: Offset, pan: Offset, zoom: Float): ZoomCommit? {
        isPinching = true
        val requested = scale * zoom
        val newScale = snapToLimits(requested.coerceIn(MIN_SCALE, MAX_SCALE))
        // The content moves with the fingers, except when they pinch past a
        // limit: then it stays put, as nothing zooms.
        val pastLimit = abs(requested - newScale) > newScale * LIMIT_SLOP
        val previousCentroid = if (pastLimit) centroid else centroid - pan
        if (newScale == scale && previousCentroid == centroid) return null
        return scaleAround(newScale, from = previousCentroid, to = centroid)
    }

    fun onPinchEnd() {
        isPinching = false
    }

    /** Horizontal drags pan the zoomed content. */
    val draggableState = DraggableState { delta -> panBy(delta) }

    /** Zooms to [target] around [focus] as if pinched there. */
    fun zoomTo(target: Float, focus: Offset): ZoomCommit =
        scaleAround(snapToLimits(target.coerceIn(MIN_SCALE, MAX_SCALE)), from = focus, to = focus)

    /** Lays the content out at [newScale], moving what was at viewport point [from] to [to]. */
    private fun scaleAround(newScale: Float, from: Offset, to: Offset): ZoomCommit {
        val step = newScale / scale
        val contentX = (from.x + panX) * step
        scale = newScale
        panX = (contentX - to.x).coerceIn(0f, maxPanX)
        return ZoomCommit(step, focusY = to.y, contentY = from.y)
    }

    private fun snapToLimits(scale: Float): Float = when {
        scale - MIN_SCALE < LIMIT_SNAP -> MIN_SCALE
        MAX_SCALE - scale < LIMIT_SNAP -> MAX_SCALE
        else -> scale
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
 * One zoom step: the content grew by [zoom], and the content that was at
 * viewport y [contentY] in the last layout should move to viewport y [focusY].
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
            var pinched = false
            try {
                do {
                    // Initial pass: see multi-touch before the list does, and
                    // consume it so the list doesn't scroll during a pinch.
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } >= 2) {
                        // Input arrives once a frame, so the list's layout
                        // already shows the previous step.
                        zoomState.onTransform(
                            centroid = event.calculateCentroid(useCurrent = true),
                            pan = event.calculatePan(),
                            zoom = event.calculateZoom(),
                        )?.let { listState.keepAnchor(it) }
                        event.changes.fastForEach { it.consume() }
                        pinched = true
                    }
                } while (event.changes.fastAny { it.pressed })
            } finally {
                if (pinched) zoomState.onPinchEnd()
            }
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
 * frame as the new scale.
 */
private fun LazyListState.keepAnchor(commit: ZoomCommit) {
    val info = layoutInfo
    val items = info.visibleItemsInfo.map { ListItemBounds(it.index, it.offset, it.size) }
    val target = anchoredScroll(items, info.viewportStartOffset, commit) ?: return
    requestScrollToItem(target.index, target.scrollOffset)
}
