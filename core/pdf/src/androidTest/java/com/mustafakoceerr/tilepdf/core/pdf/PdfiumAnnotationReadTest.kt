package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * annotated.pdf, page 1 (842 pt tall); expected positions are top-left points.
 * Pixel (150, 84) is inside the foreign highlight, above the text it covers.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumAnnotationReadTest {

    private val pdfium = Pdfium(
        InstrumentationRegistry.getInstrumentation().targetContext,
        PdfThread.dispatcher,
    )
    private lateinit var document: PdfiumDocument

    @Before
    fun setUp() = runBlocking {
        document = pdfium.open(TestAssets.uri("annotated.pdf"))
    }

    @After
    fun tearDown() = document.close()

    /** Page 0 at 1 pt = 1 px on white. */
    private fun renderPage0(): Bitmap = runBlocking {
        val size = document.pageSizes[0]
        createBitmap(size.widthPt, size.heightPt).apply {
            eraseColor(Color.WHITE)
            document.render(0, this, Matrix())
        }
    }

    private fun assertPoint(expected: PdfPoint, actual: PdfPoint) {
        assertEquals(expected.x, actual.x, 0.5f)
        assertEquals(expected.y, actual.y, 0.5f)
    }

    @Test
    fun ownInkIsRead() = runBlocking {
        val ink = document.annotations(0).own.single() as Ink
        assertEquals("tilepdf-fixture-ink", ink.id)
        assertEquals(0xFFFF0000.toInt(), ink.color)
        assertEquals(2f, ink.widthPt, 0.01f)
        val stroke = ink.strokes.single()
        assertEquals(3, stroke.size)
        assertPoint(PdfPoint(100f, 342f), stroke[0])
        assertPoint(PdfPoint(200f, 342f), stroke[1])
        assertPoint(PdfPoint(200f, 442f), stroke[2])
    }

    @Test
    fun foreignHighlightIsListed() = runBlocking {
        val highlight = document.annotations(0).foreign.single { it.key == "acrobat-1" }
        assertEquals(9, highlight.subtype)
        assertEquals("Foreign note", highlight.contents)
        assertEquals(72f, highlight.bounds.left, 0.5f)
        assertEquals(82f, highlight.bounds.top, 0.5f)
        assertEquals(260f, highlight.bounds.right, 0.5f)
        assertEquals(106f, highlight.bounds.bottom, 0.5f)
    }

    @Test
    fun unnamedForeignUsesIndexKey() = runBlocking {
        val square = document.annotations(0).foreign.single { it.subtype == 5 }
        assertEquals("#2", square.key)
    }

    @Test
    fun popupIsNotListed() = runBlocking {
        assertEquals(2, document.annotations(0).foreign.size)
    }

    @Test
    fun ownInkIsHiddenWhenRendered() {
        // Compose draws our annotations in the reader, so PDFium must not.
        assertEquals(Color.WHITE, renderPage0().getPixel(150, 342))
    }

    @Test
    fun foreignHighlightIsRendered() {
        assertNotEquals(Color.WHITE, renderPage0().getPixel(150, 84))
    }

    @Test
    fun hiddenForeignIsNotRendered() = runBlocking {
        document.setForeignHidden(0, "acrobat-1", hidden = true)
        assertEquals(Color.WHITE, renderPage0().getPixel(150, 84))
        document.setForeignHidden(0, "acrobat-1", hidden = false)
        assertNotEquals(Color.WHITE, renderPage0().getPixel(150, 84))
    }

    @Test
    fun rotatedPageHasNoAnnotations() = runBlocking {
        val page = document.annotations(1)
        assertTrue(page.own.isEmpty() && page.foreign.isEmpty())
    }
}
