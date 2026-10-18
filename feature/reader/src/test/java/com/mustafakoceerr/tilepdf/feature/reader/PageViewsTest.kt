package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.model.DocumentView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageViewsTest {

    private val sizes = listOf(PageSize(600, 800), PageSize(800, 600))
    private val crop = PdfRect(50f, 50f, 550f, 750f)

    @Test
    fun defaultViewChangesNoPage() {
        assertTrue(pageTransforms(sizes, DocumentView(), crops = null).all { it.isIdentity })
    }

    @Test
    fun rotationTurnsEveryPage() {
        val transforms = pageTransforms(sizes, DocumentView(rotation = 90), crops = null)

        assertEquals(listOf(PageSize(800, 600), PageSize(600, 800)), transforms.map { it.size })
    }

    @Test
    fun cropsApplyOnlyWhenCroppingIsOn() {
        val crops = listOf(crop, null)

        assertTrue(pageTransforms(sizes, DocumentView(cropMargins = false), crops).all { it.isIdentity })
        val cropped = pageTransforms(sizes, DocumentView(cropMargins = true), crops)
        assertEquals(listOf(PageSize(500, 700), PageSize(800, 600)), cropped.map { it.size })
    }

    @Test
    fun croppingWaitsForTheMargins() {
        assertTrue(pageTransforms(sizes, DocumentView(cropMargins = true), crops = null).all { it.isIdentity })
    }

    @Test
    fun rotationStepsClockwiseAndWraps() {
        assertEquals(listOf(90, 180, 270, 0), listOf(0, 90, 180, 270).map(::nextRotation))
    }

    @Test
    fun cropsGrowToHoldOwnAnnotations() {
        val ink = Ink("tilepdf-a", 0, 0, widthPt = 4f, strokes = listOf(listOf(PdfPoint(20f, 300f), PdfPoint(100f, 760f))))
        val note = Note("tilepdf-b", 1, 0, PdfPoint(700f, 10f), "")

        val grown = cropsWithAnnotations(listOf(crop, PdfRect(100f, 100f, 300f, 300f)), listOf(ink, note))

        assertEquals(PdfRect(18f, 50f, 550f, 762f), grown[0])
        assertEquals(PdfRect(100f, 10f, 720f, 300f), grown[1])
    }

    @Test
    fun uncroppedPagesStayUncropped() {
        val note = Note("tilepdf-b", 0, 0, PdfPoint(10f, 10f), "")

        assertEquals(listOf(null), cropsWithAnnotations(listOf(null), listOf(note)))
    }
}
