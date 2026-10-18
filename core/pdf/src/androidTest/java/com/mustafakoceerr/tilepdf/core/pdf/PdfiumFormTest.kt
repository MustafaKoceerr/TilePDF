package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.FORM_VALUE_OFF
import com.mustafakoceerr.tilepdf.model.FormFieldKind
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * forms.pdf: text "name" and "notes" (multiline), check box "agree" (Yes),
 * radio "size" (S, L), combo "city" and read-only "id" = 42. Rects are in
 * top-left points: y' = 842 - y of the file's.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumFormTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, PdfThread.dispatcher)
    private val saved = File(context.cacheDir, "forms-saved.pdf")

    private val values = mapOf(
        "name" to "Ada Lovelace",
        "notes" to "Satır bir",
        "agree" to "Yes",
        "size" to "L",
        "city" to "İzmir",
    )

    @Test
    fun listsTheFields() = runBlocking {
        pdfium.open(TestAssets.uri("forms.pdf")).use { document ->
            assertTrue(document.hasForm())
            val fields = document.formFields(0).associateBy { it.name + it.exportValue.takeIf { _ -> it.kind == FormFieldKind.RadioButton }.orEmpty() }

            assertEquals(FormFieldKind.Text, fields.getValue("name").kind)
            assertEquals(PdfRect(72f, 118f, 300f, 142f), fields.getValue("name").rect)
            assertTrue(fields.getValue("notes").multiline)
            assertEquals(FormFieldKind.CheckBox, fields.getValue("agree").kind)
            assertEquals("Yes", fields.getValue("agree").exportValue)
            assertEquals(setOf("sizeS", "sizeL"), fields.keys.filter { it.startsWith("size") }.toSet())
            assertEquals(listOf("Ankara", "İstanbul", "İzmir"), fields.getValue("city").options)
            assertTrue(fields.getValue("id").readOnly)
            assertEquals("42", fields.getValue("id").value)
        }
    }

    @Test
    fun documentWithoutFormHasNoFields() = runBlocking {
        pdfium.open(TestAssets.uri("sample.pdf")).use { document ->
            assertFalse(document.hasForm())
            assertEquals(emptyList<Any>(), document.formFields(0))
        }
    }

    @Test
    fun filledValuesReadBack() = runBlocking {
        pdfium.open(TestAssets.uri("forms.pdf")).use { document ->
            assertEquals(setOf(0), document.fill(values))

            val byName = document.formFields(0).groupBy { it.name }
            assertEquals("Ada Lovelace", byName.getValue("name").single().value)
            assertEquals("Satır bir", byName.getValue("notes").single().value)
            assertTrue(byName.getValue("agree").single().checked)
            assertEquals(listOf(false, true), byName.getValue("size").map { it.checked })
            assertEquals("İzmir", byName.getValue("city").single().value)
            // Already holding them: nothing more to do.
            assertEquals(emptySet<Int>(), document.fill(values))
        }
    }

    @Test
    fun valuesSurviveSaveAndReopen() = runBlocking {
        saved.delete()
        pdfium.openForEditing(TestAssets.uri("forms.pdf")).use { document ->
            document.fill(values)
            document.fill(mapOf("agree" to FORM_VALUE_OFF))
            document.saveIncremental(saved)
        }

        pdfium.open(Uri.fromFile(saved)).use { document ->
            val byName = document.formFields(0).groupBy { it.name }
            assertEquals("Ada Lovelace", byName.getValue("name").single().value)
            assertFalse(byName.getValue("agree").single().checked)
            assertEquals("L", byName.getValue("size").first().value)
            assertEquals("İzmir", byName.getValue("city").single().value)
        }
    }

    @Test
    fun filledFieldIsDrawn() = runBlocking {
        pdfium.open(TestAssets.uri("forms.pdf")).use { document ->
            // The middle of the check box (file rect 72..86, 560..574) at 1 pt = 1 px.
            fun centre(): Int {
                val bitmap = createBitmap(595, 842).apply { eraseColor(Color.WHITE) }
                runBlocking { document.render(0, bitmap, Matrix()) }
                return Color.red(bitmap.getPixel(79, 842 - 567))
            }
            assertTrue("empty box is light", centre() > 0xC0)
            document.fill(mapOf("agree" to "Yes"))
            // PDFium draws its own check mark, antialiased: dark, not exactly black.
            assertTrue("checked box is dark", centre() < 0x60)
        }
    }

    @Test
    fun turkishLettersInHelveticaFieldAreDrawnAndEmbedded() = runBlocking {
        saved.delete()
        val latin = pdfium.open(TestAssets.uri("forms.pdf")).use { document ->
            document.fill(mapOf("name" to "ISGisg"))
            darkPixelsOfName(document)
        }
        pdfium.openForEditing(TestAssets.uri("forms.pdf")).use { document ->
            document.fill(mapOf("name" to "İŞĞışğ"))
            // Helvetica has none of these; without the fallback font the field stays blank.
            val drawn = darkPixelsOfName(document.document)
            assertTrue("drawn: $drawn of $latin", drawn >= latin * 0.8)
            document.saveIncremental(saved)
        }

        pdfium.open(Uri.fromFile(saved)).use { document ->
            assertEquals("İŞĞışğ", document.formFields(0).single { it.name == "name" }.value)
            // Drawn from the saved appearance, so the font went into the file.
            assertTrue(darkPixelsOfName(document) >= latin * 0.8)
        }
    }

    /** Dark pixels inside the "name" field (top-left 72, 118 to 300, 142) at 1 pt = 1 px. */
    private suspend fun darkPixelsOfName(document: PdfDocument): Int {
        val bitmap = createBitmap(595, 842).apply { eraseColor(Color.WHITE) }
        document.render(0, bitmap, Matrix())
        return (72 until 300).sumOf { x -> (118 until 142).count { y -> Color.red(bitmap.getPixel(x, y)) < 0x80 } }
    }
}
