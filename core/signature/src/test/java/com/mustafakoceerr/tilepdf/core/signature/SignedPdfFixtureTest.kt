package com.mustafakoceerr.tilepdf.core.signature

import com.mustafakoceerr.tilepdf.core.pdf.PdfSignature
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * The verifier on signed.pdf, whose signature another tool made (Python's
 * cryptography): its byte range and contents are read straight from the file.
 */
class SignedPdfFixtureTest {

    private val file = File("../testing/src/main/assets/signed.pdf").readBytes()
    private val text = String(file, Charsets.ISO_8859_1)

    private fun signature(): PdfSignature {
        val range = Regex("""/ByteRange \[(\d+) (\d+) (\d+) (\d+)]""").find(text)!!.groupValues.drop(1).map { it.toLong() }
        val hex = Regex("""/Contents <([0-9a-fA-F]+)>""").find(text)!!.groupValues[1]
        val contents = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        return PdfSignature(contents, range, "adbe.pkcs7.detached", "Test", signedAt = null)
    }

    private val bytes = SignedBytes { offset, length, sink -> sink(file.copyOfRange(offset.toInt(), (offset + length).toInt())) }

    @Test
    fun theFixtureIsIntactSelfSignedAndCoversTheWholeFile() {
        val check = SignatureVerifier { emptySet() }.verify(signature(), file.size.toLong(), bytes)

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertEquals("TilePDF Test Signer", check.signer)
        assertFalse(check.trusted)
        assertFalse(check.changedAfterSigning)
    }

    @Test
    fun appendingAnUpdateIsSeenAsAChangeAfterSigning() {
        val longer = file + "\n% an incremental update\n%%EOF\n".toByteArray()

        val check = SignatureVerifier { emptySet() }.verify(
            signature(),
            longer.size.toLong(),
            { offset, length, sink -> sink(longer.copyOfRange(offset.toInt(), (offset + length).toInt())) },
        )

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertEquals(true, check.changedAfterSigning)
    }
}
