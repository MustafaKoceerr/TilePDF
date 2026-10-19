package com.mustafakoceerr.tilepdf.core.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * An incremental save writes every object PDFium has loaded. text-dense.pdf
 * has 80 pages, each with its own content stream; a note on one page must
 * not bring the other 79 pages' contents into the update.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumIncrementalSaveTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, PdfThread.dispatcher)
    private val saved = File(context.cacheDir, "text-dense-saved.pdf")

    private fun streamsIn(bytes: ByteArray): Int = Regex("\\bstream\\r?\\n").findAll(String(bytes, Charsets.ISO_8859_1)).count()

    private fun pagesIn(bytes: ByteArray): Int = Regex("/Type\\s*/Page\\b").findAll(String(bytes, Charsets.ISO_8859_1)).count()

    /** The bytes a note on page 40 adds to text-dense.pdf. */
    private fun noteUpdate(): ByteArray = runBlocking {
        val uri = TestAssets.uri("text-dense.pdf")
        val original = File(uri.path!!).length().toInt()
        saved.delete()
        pdfium.openForEditing(uri).use { document ->
            val note = Note(id = "tilepdf-note-1", pageIndex = 40, color = 0xFFFFEB3B.toInt(), at = PdfPoint(100f, 100f), text = "Not")
            assertTrue(document.write(listOf(note), emptySet()))
            document.saveIncremental(saved)
        }
        saved.readBytes().copyOfRange(original, saved.length().toInt())
    }

    @Test
    fun aNoteOnOnePageWritesOnlyThatPage() {
        val update = noteUpdate()

        // The note's appearance, the page's contents and maybe a popup's: not 80 content streams.
        assertTrue("${streamsIn(update)} streams in the update", streamsIn(update) <= 3)
    }

    @Test
    fun aNoteOnOnePageLeavesTheLaterPageDictionariesOut() {
        val update = noteUpdate()

        // PDFium reaches page 40 by loading the page dictionaries before it;
        // the 39 after it, which reading page sizes used to load, stay out.
        assertTrue("${pagesIn(update)} pages in the update", pagesIn(update) <= 41)
    }
}
