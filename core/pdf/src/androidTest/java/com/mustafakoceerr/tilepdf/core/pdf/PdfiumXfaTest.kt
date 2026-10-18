package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * xfa.pdf: a Letter XFA form. "Name" is captioned at 72..130 and edited at
 * 130..288 pt across, 108..137 pt down; its PDF page only says "Please wait".
 */
@RunWith(AndroidJUnit4::class)
class PdfiumXfaTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, PdfThread.dispatcher)
    private val saved = File(context.cacheDir, "xfa-saved.pdf")

    private val nameField = PdfPoint(200f, 122f)
    private val nameCaption = PdfPoint(90f, 122f)

    private suspend fun render(document: PdfDocument): Bitmap =
        createBitmap(612, 792).apply {
            eraseColor(Color.WHITE)
            document.render(0, this, Matrix())
        }

    private fun dark(bitmap: Bitmap, xs: IntRange, ys: IntRange) =
        xs.sumOf { x -> ys.count { y -> Color.red(bitmap.getPixel(x, y)) < 0x80 } }

    @Test
    fun theFormIsLaidOutInsteadOfThePlaceholder(): Unit = runBlocking {
        pdfium.open(TestAssets.uri("xfa.pdf")).use { document ->
            assertTrue(document.isXfa())
            assertEquals(listOf(PageSize(612, 792)), document.pageSizes)
            val bitmap = render(document)

            assertEquals("no placeholder text", 0, dark(bitmap, 72 until 200, 55 until 80))
            assertTrue("the Name caption is drawn", dark(bitmap, 72 until 128, 108 until 137) > 20)
        }
    }

    @Test
    fun aFormWithoutXfaIsNotXfa(): Unit = runBlocking {
        pdfium.open(TestAssets.uri("forms.pdf")).use { assertFalse(it.isXfa()) }
    }

    @Test
    fun tappingATextFieldGivesItsTextAndWritingDrawsIt(): Unit = runBlocking {
        pdfium.open(TestAssets.uri("xfa.pdf")).use { document ->
            assertNull("a caption isn't a text field", document.xfaTap(0, nameCaption))
            assertEquals("", document.xfaTap(0, nameField))

            document.xfaWrite(0, "Ada")

            assertTrue(dark(render(document), 132 until 288, 108 until 137) > 20)
            assertEquals("Ada", document.xfaTap(0, nameField))
        }
    }

    @Test
    fun writtenTextIsSavedIntoTheFile(): Unit = runBlocking {
        saved.delete()
        pdfium.openForEditing(TestAssets.uri("xfa.pdf")).use { document ->
            document.xfaTap(0, nameField)
            document.xfaWrite(0, "Grace Hopper")
            document.saveIncremental(saved)
        }

        pdfium.open(Uri.fromFile(saved)).use { document ->
            assertEquals("Grace Hopper", document.xfaTap(0, nameField))
        }
    }
}
