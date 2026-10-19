package com.mustafakoceerr.tilepdf.core.ocr

import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.pdf.Pdfium
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * ML Kit's bundled model on scanned.pdf: an image of three lines of text at
 * 24 pt, the first with its top 100 pt below the page top at x 72.
 */
@RunWith(AndroidJUnit4::class)
class MlKitTextRecognizerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, Dispatchers.IO.limitedParallelism(1))

    @Test
    fun readsTurkishWordsWhereTheyArePrinted(): Unit = runBlocking {
        val words = pdfium.open(TestAssets.uri("scanned.pdf")).use { document ->
            val page = document.pageSizes[0]
            val scale = 3f
            val bitmap = createBitmap((page.widthPt * scale).toInt(), (page.heightPt * scale).toInt()).apply { eraseColor(Color.WHITE) }
            document.render(0, bitmap, Matrix().apply { setScale(scale, scale) })
            recognizedWords(MlKitTextRecognizer().recognize(bitmap), scale, PageViewTransform.of(page))
        }

        val texts = words.map { it.text }
        for (expected in listOf("Taranmış", "tanıma", "İstanbul", "Şişli")) {
            assertTrue("$expected in $texts", expected in texts)
        }
        val first = words.first { it.text == "Taranmış" }.quad[0]
        assertTrue("top-left $first", first.x in 66f..80f && first.y in 96f..116f)
    }
}
