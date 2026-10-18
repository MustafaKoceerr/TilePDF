package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeBuilderTest {

    @Test
    fun firstPointKept() {
        val builder = StrokeBuilder()
        assertTrue(builder.add(PdfPoint(1f, 1f)))
        assertEquals(listOf(PdfPoint(1f, 1f)), builder.points)
    }

    @Test
    fun closePointsSkipped() {
        val builder = StrokeBuilder(minDistance = 1f)
        builder.add(PdfPoint(0f, 0f))
        assertFalse(builder.add(PdfPoint(0.5f, 0.5f)))
        assertEquals(1, builder.points.size)
    }

    @Test
    fun farPointKept() {
        val builder = StrokeBuilder(minDistance = 1f)
        builder.add(PdfPoint(0f, 0f))
        builder.add(PdfPoint(0.5f, 0f))
        assertTrue(builder.add(PdfPoint(1.2f, 0f)))
        assertEquals(listOf(PdfPoint(0f, 0f), PdfPoint(1.2f, 0f)), builder.points)
    }
}
