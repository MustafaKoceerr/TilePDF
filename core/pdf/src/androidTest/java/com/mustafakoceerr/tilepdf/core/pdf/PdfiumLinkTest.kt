package com.mustafakoceerr.tilepdf.core.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** links.pdf, page 1 (842 pt tall): three link rectangles, top-left coordinates. */
@RunWith(AndroidJUnit4::class)
class PdfiumLinkTest {

    private val pdfium = Pdfium(
        InstrumentationRegistry.getInstrumentation().targetContext,
        PdfThread.dispatcher,
    )

    private fun linkAt(x: Float, y: Float): PdfLink? = runBlocking {
        pdfium.open(TestAssets.uri("links.pdf")).use { it.linkAt(0, x, y) }
    }

    @Test
    fun goToLinkResolvesPageAndPosition() {
        // Rect y 730..760 from the bottom -> 82..112 from the top; XYZ y 642 -> 200 from the top.
        val link = linkAt(100f, 97f) as PdfLink.GoTo
        assertEquals(2, link.pageIndex)
        assertEquals(200f, link.topPt!!, 1f)
    }

    @Test
    fun uriLinkReturnsUri() {
        assertEquals(PdfLink.Uri("https://example.com/tilepdf"), linkAt(100f, 197f))
    }

    @Test
    fun javascriptUriIsReturnedRaw() {
        // The engine reports it; deciding not to follow it is the reader's job.
        assertEquals(PdfLink.Uri("javascript:alert(1)"), linkAt(100f, 297f))
    }

    @Test
    fun noLinkReturnsNull() {
        assertNull(linkAt(500f, 800f))
    }
}
