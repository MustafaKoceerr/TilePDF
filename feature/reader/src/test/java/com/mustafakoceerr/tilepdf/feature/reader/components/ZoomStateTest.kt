package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private const val DELTA = 0.001f

class ZoomStateTest {

    private lateinit var state: ZoomState

    @Before
    fun setUp() {
        state = ZoomState(exponentialDecay()).apply { viewportWidth = 1000 }
    }

    @Test
    fun pinchZoomsTheLayoutAroundTheFingers() {
        val step = state.onTransform(Offset(300f, 500f), pan = Offset.Zero, zoom = 1.5f)!!

        // Laid out at the new scale at once: content x 300 stays under the fingers.
        assertEquals(1.5f, state.scale, DELTA)
        assertEquals(150f, state.panX, DELTA) // 300 * 1.5 - 300
        assertEquals(1.5f, step.zoom, DELTA)
        assertEquals(500f, step.focusY, DELTA)
        assertEquals(500f, step.contentY, DELTA)
    }

    @Test
    fun pinchFollowsTheFingersWhileTheyMove() {
        state.onTransform(Offset(300f, 500f), pan = Offset.Zero, zoom = 1.5f)
        val step = state.onTransform(Offset(350f, 540f), pan = Offset(50f, 40f), zoom = 1.2f)!!

        // What was under the fingers last frame is under them now.
        assertEquals(1.8f, state.scale, DELTA)
        assertEquals(190f, state.panX, DELTA) // (300 + 150) * 1.2 - 350
        assertEquals(1.2f, step.zoom, DELTA)
        assertEquals(540f, step.focusY, DELTA)
        assertEquals(500f, step.contentY, DELTA)
    }

    @Test
    fun scaleIsClampedToItsRange() {
        state.onTransform(Offset.Zero, Offset.Zero, zoom = 20f)
        assertEquals(5f, state.scale, DELTA)

        state.onTransform(Offset.Zero, Offset.Zero, zoom = 0.01f)
        assertEquals(1f, state.scale, DELTA)
    }

    @Test
    fun pinchingBackToTheSmallestZoomLandsExactlyOnIt() {
        // 1.7 * (1 / 1.7) is 0.99999994 in floats; the pan range then went
        // negative and clamping the pan threw.
        state.onTransform(Offset.Zero, Offset.Zero, zoom = 1.7f)
        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 1f / 1.7f)

        assertEquals(1f, state.scale)
        assertEquals(0f, state.panX)
        assertEquals(0f, state.panBy(-30f))
    }

    @Test
    fun panIsKeptWithinThePage() {
        // Zooming around the right edge while dragging further right.
        state.onTransform(Offset(1000f, 0f), pan = Offset(-300f, 0f), zoom = 2f)

        assertEquals(1000f, state.panX, DELTA) // maxPanX = 1000 * (2 - 1)
    }

    @Test
    fun pinchingPastTheLargestZoomLeavesTheContentStill() {
        state.zoomTo(5f, Offset(500f, 400f))
        val panX = state.panX
        // Fingers still spreading, unevenly: the centroid drifts but there's no more zoom.
        val step = state.onTransform(Offset(530f, 420f), pan = Offset(30f, 20f), zoom = 1.1f)

        assertNull(step)
        assertEquals(5f, state.scale, DELTA)
        assertEquals(panX, state.panX, DELTA)
    }

    @Test
    fun pinchingPastTheSmallestZoomLeavesTheContentStill() {
        assertNull(state.onTransform(Offset(500f, 600f), pan = Offset(0f, -40f), zoom = 0.8f))
        assertEquals(1f, state.scale, DELTA)
    }

    @Test
    fun movingBothFingersStillPansAtTheLimit() {
        state.zoomTo(5f, Offset(500f, 400f))
        // Two fingers never keep exactly the same distance while dragging.
        val step = state.onTransform(Offset(500f, 440f), pan = Offset(0f, 40f), zoom = 1.001f)!!

        assertEquals(5f, state.scale, DELTA)
        assertEquals(440f, step.focusY, DELTA)
        assertEquals(400f, step.contentY, DELTA)
    }

    @Test
    fun aPinchHoldsTheListStillUntilItEnds() {
        // The list's own fling at the end of a pinch would undo the pinch's scroll.
        assertEquals(false, state.isPinching)

        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 1f)
        assertEquals(true, state.isPinching)

        state.onPinchEnd()
        assertEquals(false, state.isPinching)
    }

    @Test
    fun aPinchKeepsTheFingerLeftBehindUntilEveryFingerLifts() {
        // Otherwise the horizontal drag takes it over and jumps by all the
        // movement it saw during the pinch.
        assertEquals(false, state.handles(pressedPointers = 1))
        assertEquals(true, state.handles(pressedPointers = 2))

        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 1.2f)
        assertEquals(true, state.handles(pressedPointers = 1))

        state.onPinchEnd()
        assertEquals(false, state.handles(pressedPointers = 1))
    }

    @Test
    fun theFingerLeftBehindPansOnlyAsItMoves() {
        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 2f)
        val panX = state.panX

        // One finger lifted; the other stays where it is.
        assertNull(state.onTransform(Offset(700f, 600f), pan = Offset.Zero, zoom = 1f, pressedPointers = 1))
        assertEquals(panX, state.panX, DELTA)

        // Then it slides 30 px left: the content follows it.
        state.onTransform(Offset(670f, 600f), pan = Offset(-30f, 0f), zoom = 1f, pressedPointers = 1)
        assertEquals(panX + 30f, state.panX, DELTA)
    }

    @Test
    fun theFingerLeftBehindPansOnlyOnceItReallySlides() {
        state.touchSlop = 20f
        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 2f)
        val panX = state.panX

        // A lifting hand rolls the other finger a little: that isn't a drag.
        assertNull(state.onTransform(Offset(705f, 600f), pan = Offset(5f, 0f), zoom = 1f, pressedPointers = 1))
        assertNull(state.onTransform(Offset(710f, 600f), pan = Offset(5f, 0f), zoom = 1f, pressedPointers = 1))
        assertEquals(panX, state.panX, DELTA)

        // 25 px in all: it pans by what lies past the slop, then follows 1:1.
        state.onTransform(Offset(725f, 600f), pan = Offset(15f, 0f), zoom = 1f, pressedPointers = 1)
        assertEquals(panX - 5f, state.panX, DELTA)
        state.onTransform(Offset(735f, 600f), pan = Offset(10f, 0f), zoom = 1f, pressedPointers = 1)
        assertEquals(panX - 15f, state.panX, DELTA)
    }

    @Test
    fun aSidewaysPanNeedsNoVerticalScroll() {
        state.zoomTo(2f, Offset(500f, 400f))
        assertNull(state.onTransform(Offset(530f, 400f), pan = Offset(30f, 0f), zoom = 1f))
        assertEquals(470f, state.panX, DELTA) // 500 - 30
    }

    @Test
    fun doubleTapTogglesAroundFocus() {
        val zoomIn = state.zoomTo(state.doubleTapTarget, focus = Offset(400f, 300f))
        assertEquals(2.5f, state.scale, DELTA)
        assertEquals(600f, state.panX, DELTA) // 400 * 2.5 - 400
        assertEquals(2.5f, zoomIn.zoom, DELTA)
        assertEquals(300f, zoomIn.focusY, DELTA)
        assertEquals(300f, zoomIn.contentY, DELTA)

        state.zoomTo(state.doubleTapTarget, focus = Offset(400f, 300f))
        assertEquals(1f, state.scale, DELTA)
        assertEquals(0f, state.panX, DELTA)
    }

    @Test
    fun panIsClampedAndReportsConsumedDelta() {
        state.zoomTo(2f, Offset.Zero)

        assertEquals(-300f, state.panBy(-300f), DELTA) // drag left scrolls right
        assertEquals(300f, state.panX, DELTA)
        assertEquals(300f, state.panBy(1000f), DELTA) // only 300 left to the edge
        assertEquals(0f, state.panX, DELTA)
    }

    @Test
    fun panFractionIsRelativeToPanRange() {
        assertEquals(0f, state.panFraction, DELTA) // 1x: nothing to pan

        state.zoomTo(3f, Offset.Zero) // pan range 0..2000
        state.panBy(-500f)
        assertEquals(0.25f, state.panFraction, DELTA)
    }

    @Test
    fun savedPanIsRestoredOnceWidthIsKnown() {
        // Width unknown at construction, as when the layout hasn't run yet.
        val restored = ZoomState(exponentialDecay(), initialScale = 3f, initialPanFraction = 0.25f)
        assertEquals(0f, restored.panX, DELTA)

        // A different screen width than when saved: the fraction still holds.
        restored.viewportWidth = 800
        assertEquals(400f, restored.panX, DELTA) // 0.25 * 800 * (3 - 1)
        restored.viewportWidth = 1000 // later layouts don't re-apply it
        assertEquals(400f, restored.panX, DELTA)
    }

    @Test
    fun savedPanAppliesImmediatelyWhenWidthIsKnownUpFront() {
        val restored = ZoomState(exponentialDecay(), 2f, initialPanFraction = 1f, initialViewportWidth = 1000)
        assertEquals(1000f, restored.panX, DELTA)
    }
}
