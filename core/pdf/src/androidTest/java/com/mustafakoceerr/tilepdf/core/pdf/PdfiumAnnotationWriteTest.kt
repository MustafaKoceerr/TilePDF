package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.PageAnnotations
import com.mustafakoceerr.tilepdf.model.TextMarkup
import com.mustafakoceerr.tilepdf.model.newAnnotationId
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Edits a copy of annotated.pdf, saves it to [saved] and reads it back. On
 * page 0 the file has a foreign highlight (acrobat-1) with a popup, a foreign
 * square without /NM (#2) and our ink (tilepdf-fixture-ink).
 */
@RunWith(AndroidJUnit4::class)
class PdfiumAnnotationWriteTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, PdfThread.dispatcher)
    private val saved = File(context.cacheDir, "annotated-saved.pdf")

    private val yellow = 0xFFFFEB3B.toInt()
    private val blue = 0xFF2196F3.toInt()

    private fun edit(block: suspend (EditablePdfDocument) -> Unit) = runBlocking {
        saved.delete()
        pdfium.openForEditing(TestAssets.uri("annotated.pdf")).use { document ->
            block(document)
            document.saveIncremental(saved)
        }
    }

    private fun add(vararg annotations: Annotation) = edit {
        it.apply(AnnotationChanges(emptyMap(), annotations.toList(), emptyMap()))
    }

    private fun <T> readSaved(block: suspend (PdfiumDocument) -> T): T =
        runBlocking { pdfium.open(Uri.fromFile(saved)).use { block(it) } }

    private fun savedPage(index: Int): PageAnnotations = readSaved { it.annotations(index) }

    /**
     * Page 0 of the saved file as another viewer draws it, 1 pt = 1 px: PDFium
     * with nothing hidden, as in Chrome. Android's PdfRenderer draws no
     * annotations at all, so it can't stand in for one.
     */
    private fun viewerRender(): Bitmap = runBlocking {
        pdfium.openForEditing(Uri.fromFile(saved)).use { editable ->
            createBitmap(595, 842).apply {
                eraseColor(Color.WHITE)
                editable.document.render(0, this, Matrix())
            }
        }
    }

    private fun assertRect(expected: PdfRect, actual: PdfRect) {
        assertEquals(expected.left, actual.left, 0.5f)
        assertEquals(expected.top, actual.top, 0.5f)
        assertEquals(expected.right, actual.right, 0.5f)
        assertEquals(expected.bottom, actual.bottom, 0.5f)
    }

    private fun assertPoint(expected: PdfPoint, actual: PdfPoint) {
        assertEquals(expected.x, actual.x, 0.5f)
        assertEquals(expected.y, actual.y, 0.5f)
    }

    private fun assertMarkup(expected: TextMarkup, actual: Annotation) {
        actual as TextMarkup
        assertEquals(expected.kind, actual.kind)
        assertEquals(expected.color, actual.color)
        assertEquals(expected.text, actual.text)
        assertEquals(expected.rects.size, actual.rects.size)
        expected.rects.zip(actual.rects).forEach { (e, a) -> assertRect(e, a) }
    }

    private val lines = listOf(PdfRect(72f, 500f, 300f, 520f), PdfRect(72f, 524f, 200f, 544f))

    @Test
    fun addedAnnotationsRoundTrip() {
        val highlight = TextMarkup(newAnnotationId(), 0, yellow, MarkupKind.Highlight, lines, "two lines")
        val underline = TextMarkup(newAnnotationId(), 0, blue, MarkupKind.Underline, lines, "")
        val strikeOut = TextMarkup(newAnnotationId(), 0, Color.RED, MarkupKind.StrikeOut, lines.take(1), "struck")
        val ink = Ink(
            newAnnotationId(), 0, blue, widthPt = 3f,
            strokes = listOf(
                listOf(PdfPoint(300f, 600f), PdfPoint(350f, 620f), PdfPoint(400f, 600f)),
                listOf(PdfPoint(300f, 650f), PdfPoint(400f, 650f)),
            ),
        )
        val note = Note(newAnnotationId(), 0, yellow, PdfPoint(450f, 120f), "Remember this")
        add(highlight, underline, strikeOut, ink, note)

        val own = savedPage(0).own.associateBy { it.id }
        assertEquals(6, own.size)
        assertMarkup(highlight, own.getValue(highlight.id))
        assertMarkup(underline, own.getValue(underline.id))
        assertMarkup(strikeOut, own.getValue(strikeOut.id))
        val savedInk = own.getValue(ink.id) as Ink
        assertEquals(blue, savedInk.color)
        assertEquals(3f, savedInk.widthPt, 0.01f)
        assertEquals(ink.strokes.map { it.size }, savedInk.strokes.map { it.size })
        ink.strokes.flatten().zip(savedInk.strokes.flatten()).forEach { (e, a) -> assertPoint(e, a) }
        val savedNote = own.getValue(note.id) as Note
        assertPoint(note.at, savedNote.at)
        assertEquals(note.text, savedNote.text)
        assertEquals(yellow, savedNote.color)
    }

    @Test
    fun rotatedPageRoundTrips() {
        // Page 1 has /Rotate 90; top-left points are as displayed (842 x 595).
        val highlight = TextMarkup(newAnnotationId(), 1, yellow, MarkupKind.Highlight, listOf(PdfRect(100f, 100f, 300f, 120f)), "")
        val note = Note(newAnnotationId(), 1, yellow, PdfPoint(400f, 200f), "rotated")
        add(highlight, note)

        val own = savedPage(1).own.associateBy { it.id }
        assertMarkup(highlight, own.getValue(highlight.id))
        assertPoint(note.at, (own.getValue(note.id) as Note).at)
    }

    @Test
    fun removingOwnKeepsForeign() {
        edit { it.apply(AnnotationChanges(mapOf(0 to setOf("tilepdf-fixture-ink")), emptyList(), emptyMap())) }

        val page = savedPage(0)
        assertTrue(page.own.isEmpty())
        assertEquals(setOf("acrobat-1", "#2"), page.foreign.map { it.key }.toSet())
    }

    @Test
    fun removingForeignRemovesItsPopup() {
        edit { it.apply(AnnotationChanges(emptyMap(), emptyList(), mapOf(0 to setOf("acrobat-1")))) }

        // Highlight and popup gone; the square and our ink remain.
        assertEquals(2, readSaved { it.annotationCount(0) })
        val page = savedPage(0)
        assertEquals(listOf(5), page.foreign.map { it.subtype })
        assertEquals(listOf("tilepdf-fixture-ink"), page.own.map { it.id })
    }

    @Test
    fun removingUnnamedForeignByIndex() {
        edit {
            it.apply(AnnotationChanges(mapOf(0 to setOf("tilepdf-fixture-ink")), emptyList(), mapOf(0 to setOf("#2"))))
        }

        assertEquals(2, readSaved { it.annotationCount(0) })
        val page = savedPage(0)
        assertEquals(listOf("acrobat-1"), page.foreign.map { it.key })
        assertTrue(page.own.isEmpty())
    }

    @Test
    fun foreignAnnotationsAreKept() {
        val before = runBlocking { pdfium.open(TestAssets.uri("annotated.pdf")).use { it.annotations(0).foreign } }
        add(Note(newAnnotationId(), 0, yellow, PdfPoint(450f, 120f), "new"))

        val after = savedPage(0).foreign
        assertEquals(before.map { it.key to it.contents }, after.map { it.key to it.contents })
    }

    @Test
    fun savedAnnotationsHaveAppearanceStreams() {
        add(
            TextMarkup(newAnnotationId(), 0, yellow, MarkupKind.Highlight, lines, ""),
            Ink(newAnnotationId(), 0, blue, 2f, listOf(listOf(PdfPoint(300f, 600f), PdfPoint(400f, 600f)))),
            Note(newAnnotationId(), 0, yellow, PdfPoint(450f, 120f), "note"),
        )

        val natives = readSaved { document ->
            document.withDocument(0) { PdfiumNative.nativePageAnnotations(it, 0) }!!.filterNotNull()
        }
        val own = natives.filter { it.id?.startsWith("tilepdf-") == true }
        assertEquals(4, own.size)
        own.forEach { assertTrue("${it.id} has no /AP", it.hasAppearance) }
    }

    @Test
    fun savedOwnAnnotationsAreNotHidden() {
        add(Note(newAnnotationId(), 0, yellow, PdfPoint(450f, 120f), "note"))
        // Another viewer draws our fixture ink: it wasn't hidden in the file.
        assertNotEquals(Color.WHITE, viewerRender().getPixel(150, 342))
    }

    @Test
    fun anotherViewerShowsSavedHighlight() {
        add(TextMarkup(newAnnotationId(), 0, yellow, MarkupKind.Highlight, lines, ""))
        assertNotEquals(Color.WHITE, viewerRender().getPixel(250, 510))
    }

    @Test
    fun saveIsIncremental() {
        add(Note(newAnnotationId(), 0, yellow, PdfPoint(450f, 120f), "note"))
        val original = File(TestAssets.uri("annotated.pdf").path!!).readBytes()
        val result = saved.readBytes()
        assertTrue(result.size > original.size)
        assertArrayEquals(original, result.copyOf(original.size))
    }

    @Test
    fun noteTextRoundTrips() {
        val note = Note(newAnnotationId(), 0, yellow, PdfPoint(450f, 120f), "Not: çğışöü İ")
        add(note)
        assertEquals(note.text, (savedPage(0).own.single { it.id == note.id } as Note).text)
    }

    @Test
    fun writeReplacesTheOwnSetAndRemovesForeign() {
        val note = Note(newAnnotationId(), 0, yellow, PdfPoint(450f, 120f), "only")
        var wrote = false
        edit { wrote = it.write(listOf(note), setOf(ForeignRef(0, "#2"))) }

        assertTrue(wrote)
        val page = savedPage(0)
        assertEquals(listOf(note.id), page.own.map { it.id })
        assertEquals(listOf("acrobat-1"), page.foreign.map { it.key })
    }

    @Test
    fun nothingToWriteReturnsFalse() {
        val current = runBlocking { pdfium.open(TestAssets.uri("annotated.pdf")).use { it.annotations(0).own } }
        var wrote = true
        edit { wrote = it.write(current, emptySet()) }
        assertFalse(wrote)
    }
}
