package com.mustafakoceerr.tilepdf.core.pdf

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.OffsetDateTime

/**
 * signed.pdf: one adbe.pkcs7.detached signature over the whole file, reason
 * "Test", signed 19 Oct 2026 12:00 +03:00.
 */
@RunWith(AndroidJUnit4::class)
class PdfiumSignatureTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, PdfThread.dispatcher)
    private val saved = File(context.cacheDir, "signed-annotated.pdf")

    private fun original(): ByteArray = context.assets.open("signed.pdf").use { it.readBytes() }

    @Test
    fun signaturesAreRead(): Unit = runBlocking {
        val bytes = original()
        val signature = pdfium.open(TestAssets.uri("signed.pdf")).use { it.signatures() }.single()

        assertEquals("adbe.pkcs7.detached", signature.subFilter)
        assertEquals("Test", signature.reason)
        assertEquals(OffsetDateTime.parse("2026-10-19T12:00:00+03:00").toInstant().toEpochMilli(), signature.signedAt)
        val (start, firstLength, secondStart, secondLength) = signature.byteRange
        assertEquals(0L, start)
        assertEquals(bytes.size.toLong(), secondStart + secondLength)
        assertTrue("signature hole", secondStart > firstLength)
        // A DER sequence, maybe padded with zeros.
        assertEquals(0x30, signature.contents[0].toInt())
    }

    @Test
    fun aDocumentWithoutSignaturesHasNone(): Unit = runBlocking {
        assertEquals(emptyList<PdfSignature>(), pdfium.open(TestAssets.uri("sample.pdf")).use { it.signatures() })
    }

    @Test
    fun savingAnAnnotationKeepsTheSignedBytes(): Unit = runBlocking {
        saved.delete()
        val bytes = original()
        pdfium.openForEditing(TestAssets.uri("signed.pdf")).use { document ->
            document.apply(
                AnnotationChanges(
                    removals = emptyMap(),
                    additions = listOf(Note("tilepdf-note", 0, 0xFFFFEB3B.toInt(), PdfPoint(100f, 300f), "Okundu")),
                    foreignRemovals = emptyMap(),
                ),
            )
            document.saveIncremental(saved)
        }

        val after = saved.readBytes()
        assertTrue(after.size > bytes.size)
        assertArrayEquals(bytes, after.copyOf(bytes.size))
        pdfium.open(Uri.fromFile(saved)).use { document ->
            val signature = document.signatures().single()
            assertTrue("now ends before the file does", signature.byteRange[2] + signature.byteRange[3] < after.size)
        }
    }
}
