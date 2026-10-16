package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.render.PageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.mustafakoceerr.tilepdf.testing.TestAssets
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class PdfDocumentTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val pdfium = Pdfium(targetContext, Dispatchers.IO.limitedParallelism(1))
    private val pageRenderer = PageRenderer(Dispatchers.IO)
    private lateinit var document: PdfDocument

    @Before
    fun setUp() = runBlocking {
        document = pdfium.open(TestAssets.uri("sample.pdf"))
    }

    @After
    fun tearDown() {
        document.close()
    }

    @Test
    fun readsPageCountAndSizes() {
        assertEquals(3, document.pageCount)
        assertEquals(PageSize(595, 842), document.pageSizes[0])
        assertEquals(PageSize(842, 595), document.pageSizes[2])
    }

    @Test
    fun rendersPageAtTargetWidthWithContent() = runBlocking {
        val bitmap = pageRenderer.renderPage(document, pageIndex = 0, targetWidthPx = 400)

        assertEquals(400, bitmap.width)
        assertEquals(566, bitmap.height) // 842 * (400 / 595), rounded
        // Background is painted white; the page's black rectangle is not.
        assertEquals(Color.WHITE, bitmap.getPixel(bitmap.width - 1, 0))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue(pixels.any { it != Color.WHITE })
    }

    @Test
    fun concurrentRendersAreSerialized() = runBlocking {
        val bitmaps = (0 until 3).map { index ->
            async { pageRenderer.renderPage(document, index, 200) }
        }.map { it.await() }

        assertEquals(3, bitmaps.size)
        assertNotEquals(bitmaps[0].height, bitmaps[2].height)
    }

    @Test(expected = IOException::class)
    fun invalidFileThrowsIOException() {
        val notPdf = File(targetContext.cacheDir, "not-a.pdf").apply { writeText("hello") }
        runBlocking { pdfium.open(Uri.fromFile(notPdf)) }
    }
}
