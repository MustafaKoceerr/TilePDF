package com.mustafakoceerr.tilepdf.core.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Same expectations as PdfBoxTextProviderTest, so both engines agree on geometry. */
@RunWith(AndroidJUnit4::class)
class PdfiumTextTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, Dispatchers.IO.limitedParallelism(1))

    private fun pageText(name: String, page: Int, password: String? = null): PageText = runBlocking {
        pdfium.open(TestAssets.uri(name), password).use { it.pageText(page) }
    }

    @Test
    fun extractsPageTextInReadingOrder() {
        assertEquals("TilePDF sample page 1", pageText("sample.pdf", 0).text.trim())
    }

    @Test
    fun boxesAreInTopLeftPointSpace() {
        // 36 pt Helvetica at x = 72 pt, baseline 100 pt below the top of an 842 pt page.
        val page = pageText("sample.pdf", 0)
        val first = page.text.indices.firstNotNullOf { page.box(it) }
        assertEquals(72f, first.left, 1f)
        assertEquals(100f, first.bottom, 2f)
        assertTrue("glyph height ${first.height}", first.height in 15f..40f)
    }

    @Test
    fun landscapePageUsesItsOwnHeight() {
        // Page 3 is 842 x 595: the baseline is still 100 pt below its top.
        val page = pageText("sample.pdf", 2)
        val s = page.text.indexOf("sample")
        assertEquals(100f, page.box(s)!!.bottom, 2f)
    }

    @Test
    fun noCarriageReturns() = runBlocking {
        pdfium.open(TestAssets.uri("text-dense.pdf")).use { document ->
            for (page in 0 until document.pageCount) {
                assertFalse("page $page", '\r' in document.pageText(page).text)
            }
        }
    }

    @Test
    fun encryptedTextIsReadable() {
        assertTrue(pageText("encrypted.pdf", 0, "secret").text.contains("Encrypted page"))
    }
}
