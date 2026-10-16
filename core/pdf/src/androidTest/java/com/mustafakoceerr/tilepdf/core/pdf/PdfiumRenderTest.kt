package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class PdfiumRenderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val pdfium = Pdfium(context, dispatcher)
    private lateinit var document: PdfiumDocument

    @Before
    fun setUp() = runBlocking {
        document = pdfium.open(TestAssets.uri("sample.pdf"))
    }

    @After
    fun tearDown() = document.close()

    @Test
    fun matchesPdfRenderer() = runBlocking {
        PlatformReference(TestAssets.uri("sample.pdf")).use { reference ->
            for (page in 0 until document.pageCount) {
                val size = document.pageSizes[page]
                val transform = Matrix().apply { setScale(400f / size.widthPt, 400f / size.widthPt) }
                val expected = whiteBitmap(400, size.heightForWidth(400))
                val actual = whiteBitmap(400, size.heightForWidth(400))
                reference.render(page, expected, transform)
                document.render(page, actual, transform)
                assertSimilar("page $page", expected, actual)
            }
        }
    }

    @Test
    fun tileMatchesWholePage() = runBlocking {
        val scale = 800f / document.pageSizes[0].widthPt
        val whole = whiteBitmap(800, document.pageSizes[0].heightForWidth(800))
        document.render(0, whole, Matrix().apply { setScale(scale, scale) })

        // The tile at (64, 64) holds the start of the heading.
        val tile = whiteBitmap(256, 256)
        document.render(0, tile, Matrix().apply { setScale(scale, scale); postTranslate(-64f, -64f) })

        assertSimilar("tile", Bitmap.createBitmap(whole, 64, 64, 256, 256), tile)
    }

    @Test
    fun rejectsNonArgb8888Bitmap() = runBlocking {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565)
        expect<IllegalArgumentException> { document.render(0, bitmap, Matrix()) }
    }

    @Test
    fun pageIndexOutOfRangeThrows() = runBlocking {
        expect<IllegalArgumentException> { document.render(3, whiteBitmap(10, 10), Matrix()) }
    }

    @Test
    fun twoDocumentsRenderConcurrently() = runBlocking {
        pdfium.open(TestAssets.uri("outline.pdf")).use { other ->
            val bitmaps = (0 until 20).map { i ->
                async(Dispatchers.Default) {
                    val target = if (i % 2 == 0) document else other
                    whiteBitmap(200, 283).also { target.render(0, it, Matrix().apply { setScale(0.336f, 0.336f) }) }
                }
            }.awaitAll()
            bitmaps.forEachIndexed { i, bitmap -> assertTrue("bitmap $i is blank", hasInk(bitmap)) }
        }
    }

    @Test
    fun closeDuringRenderIsSafe() = runBlocking {
        val doomed = pdfium.open(TestAssets.uri("sample.pdf"))
        val renders = (0 until 10).map {
            async(Dispatchers.Default) {
                try {
                    doomed.render(it % 3, whiteBitmap(400, 566), Matrix().apply { setScale(0.67f, 0.67f) })
                } catch (_: IllegalStateException) {
                    // Closed before this render started: expected.
                }
            }
        }
        doomed.close()
        renders.awaitAll()
        expect<IllegalStateException> { doomed.render(0, whiteBitmap(10, 10), Matrix()) }
    }

    @Test
    fun cacheHoldsAtMostFourPages() = runBlocking {
        pdfium.open(TestAssets.uri("outline.pdf")).use { doc ->
            for (page in 0 until 6) doc.render(page, whiteBitmap(100, 142), Matrix().apply { setScale(0.17f, 0.17f) })
            assertEquals(4, doc.loadedPageCount())
        }
    }

    @Test
    fun trimMemoryReleasesLoadedPages() = runBlocking {
        val scale = Matrix().apply { setScale(0.336f, 0.336f) }
        for (page in 0 until 3) document.render(page, whiteBitmap(200, 283), scale)
        assertEquals(3, document.loadedPageCount())

        document.trimMemory()
        assertEquals(0, document.loadedPageCount())
        val after = whiteBitmap(200, 283).also { document.render(0, it, scale) }
        assertTrue(hasInk(after))
    }

    @Test
    fun cachedPagesCloseWithDocument() = runBlocking {
        // Loaded pages must be released with the document, not outlive it.
        repeat(2) {
            pdfium.open(TestAssets.uri("sample.pdf")).use { doc ->
                for (page in 0 until doc.pageCount) doc.render(page, whiteBitmap(100, 142), Matrix().apply { setScale(0.17f, 0.17f) })
            }
        }
        val after = whiteBitmap(200, 283)
        document.render(0, after, Matrix().apply { setScale(0.336f, 0.336f) })
        assertTrue(hasInk(after))
    }

    @Test
    fun rendersEveryPageAfterCacheEviction() = runBlocking {
        pdfium.open(TestAssets.uri("outline.pdf")).use { doc ->
            val scale = Matrix().apply { setScale(0.336f, 0.336f) }
            val first = whiteBitmap(200, 283).also { doc.render(0, it, scale) }
            for (page in 0 until doc.pageCount) {
                val bitmap = whiteBitmap(200, 283).also { doc.render(page, it, scale) }
                assertTrue("page $page is blank", hasInk(bitmap))
            }
            val again = whiteBitmap(200, 283).also { doc.render(0, it, scale) }
            assertSimilar("page 0 after eviction", first, again)
        }
    }

    private fun whiteBitmap(width: Int, height: Int) = createBitmap(width, height).apply { eraseColor(Color.WHITE) }

    private fun hasInk(bitmap: Bitmap): Boolean {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.any { it != Color.WHITE }
    }

    /** Same size, and at most 0.5 % of pixels differ by more than 16 in any channel. */
    private fun assertSimilar(label: String, expected: Bitmap, actual: Bitmap) {
        assertEquals("$label width", expected.width, actual.width)
        assertEquals("$label height", expected.height, actual.height)
        val count = expected.width * expected.height
        val a = IntArray(count).also { expected.getPixels(it, 0, expected.width, 0, 0, expected.width, expected.height) }
        val b = IntArray(count).also { actual.getPixels(it, 0, actual.width, 0, 0, actual.width, actual.height) }
        val differing = (0 until count).count { i ->
            listOf(16, 8, 0).any { shift -> abs((a[i] shr shift and 0xFF) - (b[i] shr shift and 0xFF)) > 16 }
        }
        assertTrue("$label: $differing of $count pixels differ", differing <= count * 0.005)
        assertTrue("$label is blank", hasInk(actual))
    }

    private inline fun <reified T : Throwable> expect(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return
            throw AssertionError("Expected ${T::class.simpleName}, got $e", e)
        }
        fail("Expected ${T::class.simpleName}")
    }
}
