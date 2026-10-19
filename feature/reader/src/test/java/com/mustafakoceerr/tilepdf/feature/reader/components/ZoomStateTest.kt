package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

private const val DELTA = 0.001f

class ZoomStateTest {

    private lateinit var state: ZoomState

    @Before
    fun setUp() {
        state = ZoomState(exponentialDecay()).apply { viewportWidth = 1000 }
    }

    /** Where the preview draws viewport point [p]. */
    private fun ZoomState.preview(p: Offset) = p * previewZoom + previewOffset

    @Test
    fun pinchKeepsCentroidFixed() {
        val centroid = Offset(300f, 500f)
        state.onTransform(centroid, pan = Offset.Zero, zoom = 1.5f)
        state.onTransform(centroid, pan = Offset.Zero, zoom = 1.2f)

        assertEquals(1.8f, state.previewZoom, DELTA)
        assertEquals(centroid.x, state.preview(centroid).x, DELTA)
        assertEquals(centroid.y, state.preview(centroid).y, DELTA)
    }

    @Test
    fun pinchIsClampedToScaleRange() {
        state.onTransform(Offset.Zero, Offset.Zero, zoom = 20f)
        assertEquals(5f, state.previewZoom, DELTA)

        state.commit()
        state.onTransform(Offset.Zero, Offset.Zero, zoom = 0.01f)
        assertEquals(0.2f, state.previewZoom, DELTA) // back to 1x overall
    }

    @Test
    fun pinchingBackToTheSmallestZoomLandsExactlyOnIt() {
        // 1.7 * (1 / 1.7) is 0.99999994 in floats; the pan range then went
        // negative and clamping the pan threw.
        state.onTransform(Offset.Zero, Offset.Zero, zoom = 1.7f)
        state.commit()
        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 0.01f)
        state.commit()

        assertEquals(1f, state.scale)
        assertEquals(0f, state.panX)
        assertEquals(0f, state.panBy(-30f))
    }

    @Test
    fun commitMatchesPreviewAtLeftEdge() {
        // Zoom 2x around the viewport centre: viewport x 250 moves to x 0.
        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 2f)
        val result = state.commit()

        assertEquals(2f, state.scale, DELTA)
        assertEquals(2f, result.zoom, DELTA)
        assertEquals(500f, state.panX, DELTA) // content x 250 * 2
        assertEquals(400f, result.focusY, DELTA)
        assertEquals(400f, result.contentY, DELTA) // the centroid's content stayed under it
        assertEquals(1f, state.previewZoom, DELTA)
        assertEquals(Offset.Zero, state.previewOffset)
    }

    @Test
    fun pinchFollowsTheFingersWhileTheyMove() {
        // The content first under the fingers stays under them as they zoom and move.
        state.onTransform(Offset(300f, 500f), pan = Offset.Zero, zoom = 1.5f)
        state.onTransform(Offset(350f, 540f), pan = Offset(50f, 40f), zoom = 1.2f)

        val content = state.preview(Offset(300f, 500f))
        assertEquals(350f, content.x, DELTA)
        assertEquals(540f, content.y, DELTA)
    }

    @Test
    fun pinchingOutPastTheSmallestZoomKeepsThePageAcrossTheScreen() {
        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 1.7f)
        state.commit()
        val contentLeft = -state.panX

        // Off-centre and further out than 1x: the page must still span the width.
        state.onTransform(Offset(800f, 400f), Offset.Zero, zoom = 0.01f)
        assertEquals(0f, state.preview(Offset(contentLeft, 0f)).x, DELTA)

        state.commit()
        assertEquals(0f, state.panX, DELTA)
    }

    @Test
    fun pinchNeverShowsPastThePageSides() {
        state.onTransform(Offset(500f, 400f), Offset.Zero, zoom = 2f)
        state.commit() // content x -500..1500 on screen

        state.onTransform(Offset(500f, 400f), pan = Offset(900f, 0f), zoom = 1f)
        assertEquals(0f, state.preview(Offset(-500f, 0f)).x, DELTA)

        state.onTransform(Offset(500f, 400f), pan = Offset(-3000f, 0f), zoom = 1f)
        assertEquals(1000f, state.preview(Offset(1500f, 0f)).x, DELTA)
    }

    @Test
    fun commitAnchorsTheContentUnderTheFingers() {
        state.zoomTo(2f, Offset.Zero)
        // Pinching out moves the content down; the anchor must still be the
        // content under the fingers, not something above the screen.
        state.onTransform(Offset(500f, 600f), Offset.Zero, zoom = 0.75f)
        state.onTransform(Offset(500f, 630f), pan = Offset(0f, 30f), zoom = 1f)
        val result = state.commit()

        assertEquals(0.75f, result.zoom, DELTA)
        assertEquals(630f, result.focusY, DELTA)
        assertEquals(600f, result.contentY, DELTA)
    }

    @Test
    fun commitClampsPanToContent() {
        // Zooming around the right edge would pan past the content end.
        state.onTransform(Offset(1000f, 0f), pan = Offset(-300f, 0f), zoom = 2f)
        state.commit()

        assertEquals(1000f, state.panX, DELTA) // maxPanX = 1000 * (2 - 1)
    }

    @Test
    fun doubleTapTogglesAroundFocus() {
        val zoomIn = state.zoomTo(state.doubleTapTarget, focus = Offset(400f, 300f))
        assertEquals(2.5f, state.scale, DELTA)
        assertEquals(300f, zoomIn.focusY, DELTA)
        assertEquals(300f, zoomIn.contentY, DELTA)
        assertEquals(600f, state.panX, DELTA) // 400 * 2.5 - 400

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
