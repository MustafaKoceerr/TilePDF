package com.mustafakoceerr.tilepdf.core.pdfwriter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

class PdfSignerTest {

    private val signedAt = ZonedDateTime.of(2026, 10, 19, 14, 5, 0, 0, ZoneOffset.ofHours(3))

    /** One page with two stamps, "stamp-a" and "stamp-b"; with [acroForm], an AcroForm holding field 9. */
    private fun document(acroForm: Boolean = false): ByteArray {
        val catalog = if (acroForm) "<< /Type /Catalog /Pages 2 0 R /AcroForm 8 0 R >>" else "<< /Type /Catalog /Pages 2 0 R >>"
        val pdf = TestPdf()
            .obj(1, catalog)
            .obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
            .obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Annots [4 0 R 6 0 R] >>")
            .obj(4, "<< /Type /Annot /Subtype /Stamp /NM (stamp-a) /Rect [100 100 280 160] /AP << /N 5 0 R >> /P 3 0 R >>")
            .obj(5, "<< /Type /XObject /Subtype /Form /BBox [0 0 180 60] /Length 0 >>\nstream\n\nendstream")
            .obj(6, "<< /Type /Annot /Subtype /Stamp /NM (stamp-b) /Rect [100 300 280 360] /P 3 0 R >>")
        val nums = mutableListOf(1, 2, 3, 4, 5, 6)
        if (acroForm) {
            pdf.obj(8, "<< /Fields [9 0 R] >>").obj(9, "<< /FT /Tx /T (name) >>")
            nums += listOf(8, 9)
        }
        pdf.xrefTable(nums, "/Size 10 /Root 1 0 R")
        return pdf.bytes()
    }

    private class Recorder(private val result: (ByteArray) -> ByteArray = { "CMS!".toByteArray() }) {
        val signed = mutableListOf<ByteArray>()
        fun sign(content: ByteArray): ByteArray = result(content).also { signed += content }
    }

    private fun sign(pdf: ByteArray, stamp: String = "stamp-a", recorder: Recorder = Recorder()) =
        PdfSigner().sign(pdf, stamp, signedAt, reason = "Onaylıyorum", sign = recorder::sign)

    private fun PdfFile.dict(ref: PdfObject?) = resolve(ref!!) as PdfDict

    @Test
    fun theStampBecomesASignatureField() {
        val file = PdfFile(sign(document()))

        val widget = file.dict(PdfRef(4))
        assertEquals(PdfName("Widget"), widget["Subtype"])
        assertEquals(PdfName("Sig"), widget["FT"])
        assertEquals(PdfRef(5), (widget["AP"] as PdfDict)["N"])
        assertEquals(PdfRef(3), widget["P"])
        val signature = file.dict(widget["V"])
        assertEquals(PdfName("Sig"), signature["Type"])
        assertEquals(PdfName("ETSI.CAdES.detached"), signature["SubFilter"])
        assertEquals(PdfString("D:20261019140500+03'00'".toByteArray()), signature["M"])
        assertEquals(PdfString("Onaylıyorum".toByteArray(Charsets.UTF_16BE).let { byteArrayOf(-2, -1) + it }), signature["Reason"])
    }

    @Test
    fun aDocumentWithoutAcroFormGetsOne() {
        val file = PdfFile(sign(document()))

        val acroForm = file.dict(file.dict(file.trailer["Root"])["AcroForm"])
        assertEquals(PdfArray(listOf(PdfRef(4))), acroForm["Fields"])
        assertEquals(PdfNumber(3), acroForm["SigFlags"])
    }

    @Test
    fun anExistingAcroFormKeepsItsFields() {
        val file = PdfFile(sign(document(acroForm = true)))

        val acroForm = file.dict(PdfRef(8))
        assertEquals(PdfArray(listOf(PdfRef(9), PdfRef(4))), acroForm["Fields"])
        assertEquals(PdfNumber(3), acroForm["SigFlags"])
    }

    @Test
    fun theByteRangeCoversEverythingButTheSignature() {
        val recorder = Recorder()
        val signed = sign(document(), recorder = recorder)

        val file = PdfFile(signed)
        val signature = file.dict(file.dict(PdfRef(4))["V"])
        val range = (signature["ByteRange"] as PdfArray).items.map { (it as PdfNumber).long.toInt() }
        assertEquals(0, range[0])
        assertEquals(signed.size, range[2] + range[3])
        assertEquals('<'.code, signed[range[1]].toInt())
        assertEquals('>'.code, signed[range[2] - 1].toInt())
        assertArrayEquals(signed.copyOfRange(0, range[1]) + signed.copyOfRange(range[2], signed.size), recorder.signed.single())
        val contents = (signature["Contents"] as PdfString).bytes
        assertArrayEquals("CMS!".toByteArray(), contents.copyOf(4))
        assertTrue(contents.drop(4).all { it == 0.toByte() })
    }

    @Test
    fun aSignatureTooBigForItsPlaceFails() {
        try {
            sign(document(), recorder = Recorder { ByteArray(PdfSigner.SIGNATURE_BYTES + 1) })
            fail("signed")
        } catch (_: PdfSignatureTooLargeException) {
        }
    }

    @Test
    fun signingAgainKeepsTheFirstSignatureIntact() {
        val first = sign(document())
        val second = sign(first, stamp = "stamp-b")

        assertArrayEquals(first, second.copyOf(first.size))
        val file = PdfFile(second)
        val fields = (file.dict(file.dict(file.trailer["Root"])["AcroForm"])["Fields"] as PdfArray).items
        assertEquals(listOf(PdfRef(4), PdfRef(6)), fields)
    }

    @Test
    fun anUnknownStampFails() {
        try {
            sign(document(), stamp = "nope")
            fail("signed")
        } catch (_: PdfFormatException) {
        }
    }

    @Test
    fun anEncryptedDocumentIsRefused() {
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            .obj(2, "<< /Type /Pages /Kids [] /Count 0 >>")
            .obj(3, "<< /Filter /Standard /V 1 /R 2 /O <00> /U <00> /P -4 >>")
        pdf.xrefTable(listOf(1, 2, 3), "/Size 4 /Root 1 0 R /Encrypt 3 0 R")

        try {
            sign(pdf.bytes())
            fail("signed")
        } catch (_: PdfEncryptedException) {
        }
    }

    @Test
    fun aStampWrittenInsideTheAnnotsArrayBecomesAnObjectOfItsOwn() {
        // As PDFium writes a new annotation: a dictionary inside the page's /Annots.
        val pdf = TestPdf()
            .obj(1, "<< /Type /Catalog /Pages 2 0 R >>")
            .obj(2, "<< /Type /Pages /Kids [3 0 R] /Count 1 >>")
            .obj(3, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Annots [5 0 R << /Type /Annot /Subtype /Stamp /NM (stamp-a) /Rect [100 100 280 160] /AP << /N 4 0 R >> >>] >>")
            .obj(4, "<< /Type /XObject /Subtype /Form /BBox [0 0 180 60] /Length 0 >>\nstream\n\nendstream")
            .obj(5, "<< /Type /Annot /Subtype /Text /Rect [0 0 20 20] >>")
        pdf.xrefTable(listOf(1, 2, 3, 4, 5), "/Size 6 /Root 1 0 R")

        val file = PdfFile(sign(pdf.bytes()))

        val annots = (file.dict(PdfRef(3))["Annots"] as PdfArray).items
        assertEquals(PdfRef(5), annots[0])
        val widgetRef = annots[1] as PdfRef
        val widget = file.dict(widgetRef)
        assertEquals(PdfName("Sig"), widget["FT"])
        assertEquals(PdfRef(3), widget["P"])
        assertEquals(PdfRef(4), (widget["AP"] as PdfDict)["N"])
        val acroForm = file.dict(file.dict(file.trailer["Root"])["AcroForm"])
        assertEquals(PdfArray(listOf(widgetRef)), acroForm["Fields"])
    }
}
