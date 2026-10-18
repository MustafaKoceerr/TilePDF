package com.mustafakoceerr.tilepdf.core.geometry

import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageViewTransformTest {

    private val a4 = PageSize(widthPt = 595, heightPt = 842)
    private val crop = PdfRect(50f, 60f, 545f, 782f)

    private fun assertPoint(expected: PdfPoint, actual: PdfPoint) {
        assertEquals("x", expected.x, actual.x, 0.001f)
        assertEquals("y", expected.y, actual.y, 0.001f)
    }

    @Test
    fun identityChangesNothing() {
        val transform = PageViewTransform.of(a4)

        assertTrue(transform.isIdentity)
        assertEquals(a4, transform.size)
        assertPoint(PdfPoint(10f, 20f), transform.toView(PdfPoint(10f, 20f)))
    }

    @Test
    fun quarterTurnsSwapTheSize() {
        assertEquals(PageSize(842, 595), PageViewTransform.of(a4, rotation = 90).size)
        assertEquals(a4, PageViewTransform.of(a4, rotation = 180).size)
        assertEquals(PageSize(842, 595), PageViewTransform.of(a4, rotation = 270).size)
    }

    @Test
    fun clockwiseQuarterTurnPutsTopLeftAtTopRight() {
        val transform = PageViewTransform.of(a4, rotation = 90)

        assertPoint(PdfPoint(842f, 0f), transform.toView(PdfPoint(0f, 0f)))
        assertPoint(PdfPoint(0f, 595f), transform.toView(PdfPoint(595f, 842f)))
    }

    @Test
    fun halfTurnFlipsBothAxes() {
        assertPoint(PdfPoint(585f, 822f), PageViewTransform.of(a4, rotation = 180).toView(PdfPoint(10f, 20f)))
    }

    @Test
    fun rotationIsNormalised() {
        assertEquals(270, PageViewTransform.of(a4, rotation = -90).rotation)
        assertEquals(0, PageViewTransform.of(a4, rotation = 360).rotation)
    }

    @Test
    fun cropMovesTheOriginAndShrinksTheSize() {
        val transform = PageViewTransform.of(a4, crop = crop)

        assertFalse(transform.isIdentity)
        assertEquals(PageSize(495, 722), transform.size)
        assertPoint(PdfPoint(0f, 0f), transform.toView(PdfPoint(50f, 60f)))
    }

    @Test
    fun cropIsRoundedOutwardAndKeptOnThePage() {
        val transform = PageViewTransform.of(a4, crop = PdfRect(-5f, 10.6f, 300.2f, 900f))

        assertEquals(PdfRect(0f, 10f, 301f, 842f), transform.crop)
    }

    @Test
    fun everyRotationRoundTrips() {
        val point = PdfPoint(123.5f, 456.25f)
        listOf(0, 90, 180, 270).forEach { rotation ->
            val transform = PageViewTransform.of(a4, rotation, crop)
            assertPoint(point, transform.toPage(transform.toView(point)))
        }
    }

    @Test
    fun rectsStayNormalised() {
        val rect = PageViewTransform.of(a4, rotation = 90).toView(PdfRect(10f, 20f, 110f, 40f))

        assertEquals(PdfRect(802f, 10f, 822f, 110f), rect)
    }

    @Test
    fun affineMatchesPointMapping() {
        val point = PdfPoint(200f, 300f)
        listOf(0, 90, 180, 270).forEach { rotation ->
            val transform = PageViewTransform.of(a4, rotation, crop)
            val m = transform.affine
            assertPoint(transform.toView(point), PdfPoint(m.a * point.x + m.c * point.y + m.tx, m.b * point.x + m.d * point.y + m.ty))
        }
    }
}
