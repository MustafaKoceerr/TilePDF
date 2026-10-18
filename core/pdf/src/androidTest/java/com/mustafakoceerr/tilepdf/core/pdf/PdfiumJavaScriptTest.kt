package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * calc.pdf: "total" is the sum of "a" and "b" with two decimals; "age"
 * alerts "Too old" and rejects values over 150.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumJavaScriptTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, PdfThread.dispatcher)

    private class RecordingHost : FormScriptHost {
        val alerts = mutableListOf<String>()
        override fun alert(message: String, title: String, buttons: Int, icon: Int): Int {
            alerts += message
            return FormScriptHost.RETURN_OK
        }
        override fun response(question: String, title: String, default: String, label: String, password: Boolean): String? = null
        override fun print() = Unit
        override fun goToPage(pageIndex: Int) = Unit
    }

    private suspend fun PdfDocument.value(name: String) = formFields(0).single { it.name == name }.value

    @Test
    fun totalCalculatesItself(): Unit = runBlocking {
        pdfium.open(TestAssets.uri("calc.pdf")).use { document ->
            document.setScriptHost(RecordingHost())

            document.fill(mapOf("a" to "2", "b" to "3"))

            assertEquals("5", document.value("total"))
        }
    }

    @Test
    fun validationAlertsAndRejects(): Unit = runBlocking {
        val host = RecordingHost()
        pdfium.open(TestAssets.uri("calc.pdf")).use { document ->
            document.setScriptHost(host)

            document.fill(mapOf("age" to "40"))
            assertEquals("40", document.value("age"))
            runCatching { document.fill(mapOf("age" to "200")) }

            assertEquals(listOf("Too old"), host.alerts)
            assertEquals("40", document.value("age"))
        }
    }

    @Test
    fun withoutAHostScriptsStillRunAndNothingWaits(): Unit = runBlocking {
        pdfium.openForEditing(TestAssets.uri("calc.pdf")).use { document ->
            document.fill(mapOf("a" to "4", "b" to "1"))
            runCatching { document.fill(mapOf("age" to "999")) }

            val fields = document.allFormFields().associate { it.name to it.value }
            assertEquals("5", fields["total"])
            assertEquals("", fields["age"])
        }
    }

    @Test
    fun totalIsDrawnWithTwoDecimals(): Unit = runBlocking {
        pdfium.open(TestAssets.uri("calc.pdf")).use { document ->
            document.setScriptHost(RecordingHost())
            document.fill(mapOf("a" to "2", "b" to "3"))

            // "total" is at top-left 72, 198 to 200, 222; "5" alone is about 7 pt wide, "5.00" about 23.
            val bitmap = createBitmap(595, 842).apply { eraseColor(Color.WHITE) }
            document.render(0, bitmap, Matrix())
            val inked = (72 until 200).filter { x -> (198 until 222).any { y -> Color.red(bitmap.getPixel(x, y)) < 0x80 } }
            assertTrue("ink from ${inked.firstOrNull()} to ${inked.lastOrNull()}", inked.last() - inked.first() > 15)
        }
    }
}
