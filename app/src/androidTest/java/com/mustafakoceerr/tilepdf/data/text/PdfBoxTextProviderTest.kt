package com.mustafakoceerr.tilepdf.data.text

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfBoxTextProviderTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var provider: PdfTextProvider

    @Before
    fun setUp() = runBlocking {
        val file = File(context.cacheDir, "sample.pdf")
        instrumentation.context.assets.open("sample.pdf").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        provider = PdfBoxTextProvider.Factory(context, Dispatchers.IO).create(Uri.fromFile(file).toString())
    }

    @After
    fun tearDown() = runBlocking { provider.close() }

    @Test
    fun extractsPageTextInReadingOrder() = runBlocking {
        val text = provider.pageText(0).chars.joinToString("") { it.char.toString() }
        assertEquals("TilePDF sample page 1", text.trim())
    }

    @Test
    fun boxesAreInTopLeftPointSpace() = runBlocking {
        // The sample draws 36 pt Helvetica at x = 72 pt with its baseline
        // 100 pt below the top of an 842 pt tall page.
        val first = provider.pageText(0).chars.first { it.box != null }.box!!
        assertEquals(72f, first.left, 1f)
        assertEquals(100f, first.bottom, 2f)
        assertTrue("glyph height ${first.height}", first.height in 15f..40f)
    }

    @Test
    fun searchFindsPhraseIgnoringCase() = runBlocking {
        val matches = provider.search(0, "tilepdf SAMPLE")
        assertEquals(1, matches.size)
        val rect = matches.single().rects.single()
        assertEquals(72f, rect.left, 1f)
        assertTrue(rect.right > 200f)

        assertTrue(provider.search(0, "page 2").isEmpty())
        assertEquals(1, provider.search(1, "page 2").size)
    }

    @Test
    fun landscapePageUsesItsOwnHeight() = runBlocking {
        // Page 3 is 842 x 595: the baseline is still 100 pt below its top.
        val rect = provider.search(2, "sample").single().rects.single()
        assertEquals(100f, rect.bottom, 2f)
    }
}
