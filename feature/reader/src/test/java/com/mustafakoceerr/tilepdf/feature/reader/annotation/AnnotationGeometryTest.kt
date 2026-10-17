package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.feature.reader.SelectionUiState
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationGeometryTest {

    private val yellow = 0xFFFFEB3B.toInt()

    private fun highlight(id: String, rect: PdfRect, page: Int = 0): Annotation =
        TextMarkup(id, page, yellow, MarkupKind.Highlight, listOf(rect), "")

    private fun ink(id: String, vararg points: PdfPoint, width: Float = 2f, page: Int = 0): Annotation =
        Ink(id, page, yellow, width, listOf(points.toList()))

    private fun foreign(key: String, bounds: PdfRect, subtype: Int = 9, strokes: List<List<PdfPoint>> = emptyList()) =
        ForeignAnnotation(0, key, subtype, bounds, "", strokes)

    private val line = arrayOf(PdfPoint(100f, 100f), PdfPoint(200f, 100f))

    @Test
    fun markupTakesSelectionRectsAndText() {
        val rects = listOf(PdfRect(1f, 2f, 3f, 4f), PdfRect(5f, 6f, 7f, 8f))
        val selection = SelectionUiState(4, "two lines", rects, rects.first(), rects.last())

        val markup = markupFor(selection, MarkupKind.Underline, yellow, id = "tilepdf-x")

        assertEquals(TextMarkup("tilepdf-x", 4, yellow, MarkupKind.Underline, rects, "two lines"), markup)
    }

    @Test
    fun markupHitInsideRect() {
        val target = highlight("a", PdfRect(100f, 100f, 200f, 120f))
        assertEquals(AnnotationTarget.Own(target), annotationAt(listOf(target), emptyList(), 0, PdfPoint(150f, 110f)))
    }

    @Test
    fun markupMissFarAway() {
        assertNull(annotationAt(listOf(highlight("a", PdfRect(100f, 100f, 200f, 120f))), emptyList(), 0, PdfPoint(150f, 140f)))
    }

    @Test
    fun inkHitNearSegment() {
        val target = ink("a", *line)
        // Half the width (1) plus the tolerance (6): 7 pt.
        assertEquals(AnnotationTarget.Own(target), annotationAt(listOf(target), emptyList(), 0, PdfPoint(150f, 105f)))
        assertNull(annotationAt(listOf(target), emptyList(), 0, PdfPoint(150f, 108f)))
    }

    @Test
    fun dotStrokeHits() {
        val target = ink("a", PdfPoint(50f, 50f))
        assertEquals(AnnotationTarget.Own(target), annotationAt(listOf(target), emptyList(), 0, PdfPoint(53f, 52f)))
    }

    @Test
    fun noteHitInsideIcon() {
        val note = Note("n", 0, yellow, PdfPoint(300f, 300f), "x")
        assertEquals(AnnotationTarget.Own(note), annotationAt(listOf(note), emptyList(), 0, PdfPoint(318f, 318f)))
        assertNull(annotationAt(listOf(note), emptyList(), 0, PdfPoint(330f, 330f)))
    }

    @Test
    fun latestOwnWins() {
        val first = highlight("a", PdfRect(100f, 100f, 200f, 120f))
        val second = highlight("b", PdfRect(150f, 100f, 250f, 120f))
        assertEquals(AnnotationTarget.Own(second), annotationAt(listOf(first, second), emptyList(), 0, PdfPoint(160f, 110f)))
    }

    @Test
    fun ownBeforeForeign() {
        val own = highlight("a", PdfRect(100f, 100f, 200f, 120f))
        val other = foreign("acrobat-1", PdfRect(100f, 100f, 200f, 120f))
        assertEquals(AnnotationTarget.Own(own), annotationAt(listOf(own), listOf(other), 0, PdfPoint(150f, 110f)))
        assertEquals(AnnotationTarget.Foreign(other), annotationAt(emptyList(), listOf(other), 0, PdfPoint(150f, 110f)))
    }

    @Test
    fun otherPageIgnored() {
        val target = highlight("a", PdfRect(100f, 100f, 200f, 120f), page = 1)
        assertNull(annotationAt(listOf(target), emptyList(), 0, PdfPoint(150f, 110f)))
    }

    @Test
    fun foreignInkUsesStrokes() {
        val other = foreign("i", PdfRect(0f, 0f, 500f, 500f), subtype = 15, strokes = listOf(line.toList()))
        // Inside the bounds but far from the stroke.
        assertNull(annotationAt(emptyList(), listOf(other), 0, PdfPoint(300f, 300f)))
        assertEquals(AnnotationTarget.Foreign(other), annotationAt(emptyList(), listOf(other), 0, PdfPoint(150f, 103f)))
    }

    @Test
    fun eraserHitsCrossedInk() {
        val crossed = ink("a", *line)
        val untouched = ink("b", PdfPoint(100f, 300f), PdfPoint(200f, 300f))

        val erased = erasedBy(listOf(crossed, untouched), emptyList(), 0, PdfPoint(150f, 80f), PdfPoint(150f, 120f))

        assertEquals(Erased(setOf("a"), emptySet()), erased)
    }

    @Test
    fun eraserIgnoresMarkupAndNotes() {
        val markup = highlight("m", PdfRect(100f, 90f, 200f, 110f))
        val note = Note("n", 0, yellow, PdfPoint(140f, 90f), "")

        val erased = erasedBy(listOf(markup, note), emptyList(), 0, PdfPoint(150f, 80f), PdfPoint(150f, 120f))

        assertTrue(erased.ids.isEmpty() && erased.refs.isEmpty())
    }

    @Test
    fun eraserHitsForeignInk() {
        val other = foreign("i", PdfRect(100f, 100f, 200f, 100f), subtype = 15, strokes = listOf(line.toList()))
        val erased = erasedBy(emptyList(), listOf(other), 0, PdfPoint(150f, 80f), PdfPoint(150f, 120f))
        assertEquals(setOf(ForeignRef(0, "i")), erased.refs)
    }
}
