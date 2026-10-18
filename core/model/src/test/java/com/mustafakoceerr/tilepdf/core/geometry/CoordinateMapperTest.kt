package com.mustafakoceerr.tilepdf.core.geometry

import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CoordinateMapperTest {

    private val a4 = PageSize(widthPt = 595, heightPt = 842)

    @Test
    fun scalesPointsToLaidOutPageWidth() {
        val rect = PdfRect(59.5f, 84.2f, 119f, 168.4f)

        // Page laid out 1190 px wide: 2 px per point.
        assertEquals(
            PixelRectF(119f, 168.4f, 238f, 336.8f),
            CoordinateMapper.toPagePixels(rect, a4, pageWidthPx = 1190f),
        )
    }

    @Test
    fun pointMappingInvertsRectMapping() {
        val rect = CoordinateMapper.toPagePixels(PdfRect(100f, 200f, 100f, 200f), a4, pageWidthPx = 1333f)
        val (x, y) = CoordinateMapper.toPdfPoints(rect.left, rect.top, a4, pageWidthPx = 1333f)

        assertEquals(100f, x, 0.001f)
        assertEquals(200f, y, 0.001f)
    }

    @Test
    fun matchesRendererScaleForTheSameWidth() {
        // A point at the page's right edge maps to the rendered width exactly.
        val edge = PdfRect(595f, 842f, 595f, 842f)
        val mapped = CoordinateMapper.toPagePixels(edge, a4, pageWidthPx = 1080f)

        assertEquals(1080f, mapped.right, 0.001f)
        assertEquals(a4.heightForWidth(1080).toFloat(), mapped.bottom, 0.5f)
    }

    @Test
    fun rotatedPageMapsRectsIntoTheTurnedLayout() {
        val turned = PageViewTransform.of(a4, rotation = 90)

        // 842 pt wide when turned; laid out 1684 px wide: 2 px per point.
        assertEquals(
            PixelRectF(1604f, 20f, 1644f, 220f),
            CoordinateMapper.toPagePixels(PdfRect(10f, 20f, 110f, 40f), turned, pageWidthPx = 1684f),
        )
    }

    @Test
    fun touchOnRotatedPageLandsOnPagePoint() {
        val turned = PageViewTransform.of(a4, rotation = 270)

        val point = CoordinateMapper.toPdfPoints(0f, 0f, turned, pageWidthPx = 842f)

        assertEquals(PdfPoint(595f, 0f), point)
    }

    @Test
    fun croppedPageMapsAroundTheCrop() {
        val cropped = PageViewTransform.of(a4, crop = PdfRect(100f, 100f, 400f, 500f))
        val rect = CoordinateMapper.toPagePixels(PdfRect(100f, 100f, 400f, 500f), cropped, pageWidthPx = 600f)

        assertEquals(PixelRectF(0f, 0f, 600f, 800f), rect)
        assertEquals(PdfPoint(250f, 300f), CoordinateMapper.toPdfPoints(300f, 400f, cropped, pageWidthPx = 600f))
    }
}
