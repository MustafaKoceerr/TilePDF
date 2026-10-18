package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeAnnotationMappingTest {

    private fun native(
        subtype: Int,
        id: String? = null,
        index: Int = 0,
        contents: String? = null,
        rect: FloatArray = floatArrayOf(10f, 20f, 30f, 40f),
        points: FloatArray = FloatArray(0),
        pathSizes: IntArray = IntArray(0),
    ) = NativeAnnotation(subtype, index, id, contents, 0xFF112233.toInt(), 1.5f, rect, points, pathSizes, true)

    @Test
    fun ownHighlightBecomesMarkupWithOneRectPerQuad() {
        // Quads as TL, TR, BL, BR corners in top-left points.
        val quads = floatArrayOf(
            10f, 20f, 50f, 20f, 10f, 30f, 50f, 30f,
            10f, 32f, 40f, 32f, 10f, 42f, 40f, 42f,
        )
        val markup = toPageAnnotations(3, arrayOf(native(9, "tilepdf-a", contents = "words", points = quads)))
            .own.single() as TextMarkup
        assertEquals(MarkupKind.Highlight, markup.kind)
        assertEquals(3, markup.pageIndex)
        assertEquals(listOf(PdfRect(10f, 20f, 50f, 30f), PdfRect(10f, 32f, 40f, 42f)), markup.rects)
        assertEquals("words", markup.text)
        assertEquals(0xFF112233.toInt(), markup.color)
    }

    @Test
    fun underlineAndStrikeOutKinds() {
        val page = toPageAnnotations(0, arrayOf(native(10, "tilepdf-u"), native(12, "tilepdf-s")))
        assertEquals(listOf(MarkupKind.Underline, MarkupKind.StrikeOut), page.own.map { (it as TextMarkup).kind })
    }

    @Test
    fun ownInkSplitsPaths() {
        val ink = toPageAnnotations(
            0,
            arrayOf(native(15, "tilepdf-i", points = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f), pathSizes = intArrayOf(2, 1))),
        ).own.single() as Ink
        assertEquals(listOf(listOf(PdfPoint(1f, 2f), PdfPoint(3f, 4f)), listOf(PdfPoint(5f, 6f))), ink.strokes)
        assertEquals(1.5f, ink.widthPt)
    }

    @Test
    fun ownNoteIsAtItsRectTopLeft() {
        val note = toPageAnnotations(0, arrayOf(native(1, "tilepdf-n", contents = null))).own.single() as Note
        assertEquals(PdfPoint(10f, 20f), note.at)
        assertEquals("", note.text)
    }

    @Test
    fun ownOfUnsupportedSubtypeIsSkipped() {
        assertTrue(toPageAnnotations(0, arrayOf(native(5, "tilepdf-square"))).own.isEmpty())
    }

    @Test
    fun foreignUsesNameOrIndexAsKey() {
        val page = toPageAnnotations(0, arrayOf(native(9, "acrobat-1", index = 0, contents = "hi"), native(5, null, index = 2)))
        assertEquals(listOf("acrobat-1", "#2"), page.foreign.map { it.key })
        assertEquals(PdfRect(10f, 20f, 30f, 40f), page.foreign[0].bounds)
        assertEquals("hi", page.foreign[0].contents)
        assertEquals("", page.foreign[1].contents)
    }

    @Test
    fun foreignInkKeepsStrokes() {
        val foreign = toPageAnnotations(0, arrayOf(native(15, "other", points = floatArrayOf(1f, 2f), pathSizes = intArrayOf(1))))
            .foreign.single()
        assertEquals(listOf(listOf(PdfPoint(1f, 2f))), foreign.inkStrokes)
    }

    @Test
    fun linksPopupsAndWidgetsAreSkipped() {
        // Link 2, Popup 16, Widget 20.
        val page = toPageAnnotations(0, arrayOf(native(2), native(16), native(20)))
        assertTrue(page.own.isEmpty() && page.foreign.isEmpty())
    }
}
