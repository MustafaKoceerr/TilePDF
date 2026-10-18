package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.OcrWord
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Invisible text for recognised words, on page 0 of sample.pdf (A4, text only near the top). */
@RunWith(AndroidJUnit4::class)
class PdfiumOcrWriteTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, Dispatchers.IO.limitedParallelism(1))
    private val saved = File(context.cacheDir, "ocr-saved.pdf")

    private fun quad(left: Float, top: Float, right: Float, bottom: Float) =
        listOf(PdfPoint(left, top), PdfPoint(right, top), PdfPoint(right, bottom), PdfPoint(left, bottom))

    private val words = listOf(
        OcrWord("Taranmış", quad(100f, 500f, 190f, 516f)),
        OcrWord("sayfa", quad(196f, 500f, 250f, 516f)),
    )

    @Before
    fun setUp() {
        saved.delete()
    }

    @Test
    fun wordsBecomeTextWhereTheyWereRead() = runBlocking {
        pdfium.open(TestAssets.uri("sample.pdf")).use { document ->
            assertFalse(document.hasRecognizedText(0))

            document.addRecognizedText(0, words)

            val text = document.pageText(0)
            val at = text.text.indexOf("Taranmış")
            assertTrue("text: ${text.text}", at >= 0)
            assertTrue(text.text.contains("sayfa"))
            val box = text.box(at)!!
            assertEquals(100f, box.left, 4f)
            assertTrue("box $box", box.top in 496f..516f && box.bottom in 500f..520f)
            assertTrue(document.hasRecognizedText(0))
        }
    }

    @Test
    fun thePageDrawsAsBefore() = runBlocking {
        pdfium.open(TestAssets.uri("sample.pdf")).use { document ->
            fun pixels(): IntArray {
                val bitmap = createBitmap(595, 842).apply { eraseColor(Color.WHITE) }
                runBlocking { document.render(0, bitmap, Matrix()) }
                return IntArray(595 * 842).also { bitmap.getPixels(it, 0, 595, 0, 0, 595, 842) }
            }
            val before = pixels()

            document.addRecognizedText(0, words)

            assertTrue(before.contentEquals(pixels()))
        }
    }

    @Test
    fun wordsSurviveSaveAndReopen() = runBlocking {
        pdfium.openForEditing(TestAssets.uri("sample.pdf")).use { document ->
            document.addRecognizedText(0, words)
            document.saveIncremental(saved)
        }

        pdfium.open(Uri.fromFile(saved)).use { document ->
            assertTrue(document.hasRecognizedText(0))
            assertFalse(document.hasRecognizedText(1))
            assertTrue(document.pageText(0).text.contains("Taranmış"))
        }
    }
}
