package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DELTA = 0.01f

class PageZoomTest {

    private val a4 = PageSize(595, 842)
    private val portraitScreen = IntSize(1080, 2000)
    private val fitted = fitPage(a4, portraitScreen)

    /** Where the page draws its own pixel [p] on screen. */
    private fun PageZoom.screenOf(p: Offset) =
        Offset(fitted.left + offset.x + p.x * scale, fitted.top + offset.y + p.y * scale)

    @Test
    fun fitPortraitOnPortraitFillsWidth() {
        assertEquals(PixelRect.ofSize(0, 236, 1080, 1528), fitted)
    }

    @Test
    fun fitLandscapeOnPortraitFillsWidth() {
        val rect = fitPage(PageSize(842, 595), portraitScreen)
        assertEquals(1080, rect.width)
        assertEquals(763, rect.height)
        assertEquals(0, rect.left)
    }

    @Test
    fun fitPortraitOnLandscapeFillsHeight() {
        assertEquals(PixelRect.ofSize(618, 0, 763, 1080), fitPage(a4, IntSize(2000, 1080)))
    }

    @Test
    fun pinchKeepsCentroidFixed() {
        val zoom = PageZoom(fitted, portraitScreen)
        val centroid = Offset(540f, 1000f)
        val before = zoom.toPagePixels(centroid)!!

        zoom.transform(centroid, pan = Offset.Zero, zoom = 1.5f)
        zoom.transform(centroid, pan = Offset.Zero, zoom = 1.2f)

        assertEquals(1.8f, zoom.scale, DELTA)
        assertEquals(centroid.x, zoom.screenOf(before).x, 0.5f)
        assertEquals(centroid.y, zoom.screenOf(before).y, 0.5f)
    }

    @Test
    fun scaleIsClamped() {
        val zoom = PageZoom(fitted, portraitScreen)
        zoom.transform(Offset(540f, 1000f), Offset.Zero, zoom = 20f)
        assertEquals(5f, zoom.scale, DELTA)
        zoom.transform(Offset(540f, 1000f), Offset.Zero, zoom = 0.01f)
        assertEquals(1f, zoom.scale, DELTA)
    }

    @Test
    fun unzoomedPageIsCentred() {
        val zoom = PageZoom(fitted, portraitScreen)
        zoom.transform(Offset(540f, 1000f), pan = Offset(500f, 300f), zoom = 1f)
        assertEquals(0f, zoom.offset.x, 1f)
        assertEquals(0f, zoom.offset.y, 1f)
    }

    @Test
    fun panStopsAtEdges() {
        val zoom = PageZoom(fitted, portraitScreen)
        zoom.transform(Offset(540f, 1000f), Offset.Zero, zoom = 2f)

        val consumed = zoom.panBy(Offset(10_000f, 0f))

        // The page's left edge stops at the screen's left edge.
        assertEquals(0f, zoom.screenOf(Offset.Zero).x, 0.5f)
        assertTrue(consumed.x < 10_000f)

        zoom.panBy(Offset(-10_000f, -10_000f))
        assertEquals(1080f, zoom.screenOf(Offset(1080f, 1528f)).x, 0.5f)
        assertEquals(2000f, zoom.screenOf(Offset(1080f, 1528f)).y, 0.5f)
    }

    @Test
    fun doubleTapTogglesZoom() {
        val zoom = PageZoom(fitted, portraitScreen)
        zoom.doubleTap(Offset(540f, 1000f))
        assertEquals(2.5f, zoom.scale, DELTA)
        zoom.doubleTap(Offset(540f, 1000f))
        assertEquals(1f, zoom.scale, DELTA)
        assertEquals(0f, zoom.offset.x, 1f)
        assertEquals(0f, zoom.offset.y, 1f)
    }

    @Test
    fun tapAt2xMapsToPagePoint() {
        val zoom = PageZoom(fitted, portraitScreen)
        val centre = Offset(540f, 236f + 764f)
        zoom.transform(centre, Offset.Zero, zoom = 2f)

        val point = zoom.toPagePixels(centre)!!

        assertEquals(540f, point.x, 0.5f)
        assertEquals(764f, point.y, 0.5f)
    }

    @Test
    fun tapOffPageIsNull() {
        val zoom = PageZoom(fitted, portraitScreen)
        // Above the page, in the letterbox.
        assertNull(zoom.toPagePixels(Offset(540f, 100f)))
        assertNotNull(zoom.toPagePixels(Offset(540f, 300f)))
    }

    @Test
    fun pageBoundsFollowZoom() {
        val zoom = PageZoom(fitted, portraitScreen)
        zoom.transform(Offset(540f, 1000f), Offset.Zero, zoom = 2f)

        val bounds = zoom.pageBounds()

        assertEquals(2160, bounds.width)
        assertEquals(3056, bounds.height)
        assertEquals(zoom.screenOf(Offset.Zero).x.toInt(), bounds.left)
    }
}
