package com.mustafakoceerr.tilepdf.core.signature

import com.mustafakoceerr.tilepdf.core.pdf.PdfSignature
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfArray
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfDict
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfFile
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfName
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfNumber
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfRef
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfSigner
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfString
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cms.CMSSignedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.TrustAnchor
import java.time.ZonedDateTime

class CmsSignerTest {

    private val rootKeys = TestCertificates.keys()
    private val root = TestCertificates.certificate("CN=Test Root", rootKeys, ca = true)
    private val signerKeys = TestCertificates.keys()
    private val signer = TestCertificates.certificate("CN=Grace Hopper", signerKeys, issuer = "CN=Test Root", signingKey = rootKeys.private)
    private val cms = CmsSigner(signerKeys.private, listOf(signer, root))

    @Test
    fun theSignedAttributesArePadesBaseline() {
        val signed = CMSSignedData(cms.sign("content".toByteArray()))
        val attributes = signed.signerInfos.signers.single().signedAttributes

        assertNotNull(attributes.get(PKCSObjectIdentifiers.id_aa_signingCertificateV2))
        assertNotNull(attributes.get(CMSAttributes.messageDigest))
        assertNotNull(attributes.get(CMSAttributes.contentType))
        assertNull("PAdES leaves the time to /M", attributes.get(CMSAttributes.signingTime))
        assertEquals(2, signed.certificates.getMatches(null).size)
    }

    /** One page with a stamp named "stamp-a", objects written with a classic xref table. */
    private fun document(): ByteArray {
        val objects = listOf(
            "<< /Type /Catalog /Pages 2 0 R >>",
            "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Annots [4 0 R] >>",
            "<< /Type /Annot /Subtype /Stamp /NM (stamp-a) /Rect [100 100 280 160] /P 3 0 R >>",
        )
        val out = StringBuilder("%PDF-1.7\n")
        val offsets = objects.mapIndexed { i, body ->
            out.length.also { out.append("${i + 1} 0 obj\n$body\nendobj\n") }
        }
        val xref = out.length
        out.append("xref\n0 ${objects.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append("%010d 00000 n \n".format(it)) }
        out.append("trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return out.toString().toByteArray(Charsets.ISO_8859_1)
    }

    @Test
    fun aDocumentWeSignVerifiesAsIntactAndTrusted() {
        val signed = PdfSigner().sign(document(), "stamp-a", ZonedDateTime.now(), "Test", cms::sign)

        val file = PdfFile(signed)
        val dict = file.resolve((file.resolve(PdfRef(4)) as PdfDict)["V"]!!) as PdfDict
        val signature = PdfSignature(
            contents = (dict["Contents"] as PdfString).bytes,
            byteRange = (dict["ByteRange"] as PdfArray).items.map { (it as PdfNumber).long },
            subFilter = (dict["SubFilter"] as PdfName).name,
            reason = "Test",
            signedAt = System.currentTimeMillis(),
        )
        val check = SignatureVerifier { setOf(TrustAnchor(root, null)) }.verify(signature, signed.size.toLong()) { offset, length, sink ->
            sink(signed.copyOfRange(offset.toInt(), (offset + length).toInt()))
        }

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertTrue(check.trusted)
        assertFalse(check.changedAfterSigning)
        assertEquals("Grace Hopper", check.signer)
    }
}
