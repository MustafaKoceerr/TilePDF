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
}
