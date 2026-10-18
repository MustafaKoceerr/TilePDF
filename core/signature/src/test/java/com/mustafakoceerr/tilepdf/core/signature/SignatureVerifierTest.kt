package com.mustafakoceerr.tilepdf.core.signature

import com.mustafakoceerr.tilepdf.core.pdf.PdfSignature
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit

class SignatureVerifierTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = System.currentTimeMillis()

    private fun keys(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private fun certificate(
        subject: String,
        keys: KeyPair,
        issuer: String = subject,
        signingKey: PrivateKey = keys.private,
        from: Long = now - day,
        to: Long = now + 365 * day,
        ca: Boolean = false,
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            X500Name(issuer), BigInteger.valueOf(now + subject.hashCode()), Date(from), Date(to), X500Name(subject), keys.public,
        )
        if (ca) builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        val holder: X509CertificateHolder = builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey))
        return JcaX509CertificateConverter().getCertificate(holder)
    }

    private val rootKeys = keys()
    private val root = certificate("CN=Test Root", rootKeys, ca = true)
    private val signerKeys = keys()
    private val signer = certificate("CN=Ada Lovelace,O=Analytical", signerKeys, issuer = "CN=Test Root", signingKey = rootKeys.private)
    private val verifier = SignatureVerifier { setOf(TrustAnchor(root, null)) }

    /** A file whose signature hole holds [contents]: the signed bytes are before and after it. */
    private class SignedFile(val bytes: ByteArray, val byteRange: List<Long>)

    private val before = "%PDF-1.7\n1 0 obj << /Contents <".toByteArray()
    private val after = "> /ByteRange [...] >>\nendobj\n%%EOF\n".toByteArray()
    private val holeLength = 16384

    private fun signedContent() = before + after

    private fun cms(content: ByteArray, certificate: X509Certificate = signer, key: PrivateKey = signerKeys.private, chain: List<X509Certificate> = listOf(signer, root)): ByteArray {
        val generator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(
                JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
                    .build(JcaContentSignerBuilder("SHA256withRSA").build(key), certificate),
            )
            addCertificates(JcaCertStore(chain))
        }
        return generator.generate(CMSProcessableByteArray(content), false).encoded
    }

    private fun file(contents: ByteArray, appended: ByteArray = ByteArray(0)): SignedFile {
        val hex = contents.joinToString("") { "%02x".format(it) }.padEnd(holeLength, '0').toByteArray()
        val bytes = before + hex + after + appended
        return SignedFile(bytes, listOf(0L, before.size.toLong(), (before.size + hex.size).toLong(), after.size.toLong()))
    }

    private fun verify(file: SignedFile, contents: ByteArray, subFilter: String = "adbe.pkcs7.detached", reason: String = "") =
        verifier.verify(
            PdfSignature(contents, file.byteRange, subFilter, reason, signedAt = null),
            fileLength = file.bytes.size.toLong(),
            bytes = { offset, length, sink -> sink(file.bytes.copyOfRange(offset.toInt(), (offset + length).toInt())) },
        )

    @Test
    fun wholeFileSignatureIsIntactAndTrusted() {
        val contents = cms(signedContent())

        val check = verify(file(contents), contents)

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertTrue(check.trusted)
        assertFalse(check.changedAfterSigning)
    }

    @Test
    fun appendedUpdateIsChangedAfterSigning() {
        val contents = cms(signedContent())

        val check = verify(file(contents, appended = "2 0 obj << >> endobj\n%%EOF\n".toByteArray()), contents)

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertTrue(check.changedAfterSigning)
    }

    @Test
    fun flippedByteIsBroken() {
        val contents = cms(signedContent())
        val tampered = file(contents).let { original ->
            SignedFile(original.bytes.copyOf().also { it[3] = 'X'.code.toByte() }, original.byteRange)
        }

        assertEquals(SignatureIntegrity.Broken, verify(tampered, contents).integrity)
    }

    @Test
    fun selfSignedIsUntrusted() {
        val ownKeys = keys()
        val own = certificate("CN=Someone", ownKeys)
        val contents = cms(signedContent(), own, ownKeys.private, listOf(own))

        val check = verify(file(contents), contents)

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertFalse(check.trusted)
    }

    @Test
    fun expiredBeforeSigningIsUntrusted() {
        val oldKeys = keys()
        val old = certificate(
            "CN=Old", oldKeys, issuer = "CN=Test Root", signingKey = rootKeys.private, from = now - 400 * day, to = now - 30 * day,
        )
        val contents = cms(signedContent(), old, oldKeys.private, listOf(old, root))

        val check = verify(file(contents), contents)

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertFalse(check.trusted)
    }

    @Test
    fun unknownSubFilterIsUnsupported() {
        val contents = cms(signedContent())

        assertEquals(SignatureIntegrity.Unsupported, verify(file(contents), contents, subFilter = "adbe.x509.rsa_sha1").integrity)
    }

    @Test
    fun garbageContentsIsBroken() {
        val contents = ByteArray(64) { 7 }

        val check = verify(file(contents), contents)

        assertEquals(SignatureIntegrity.Broken, check.integrity)
        assertFalse(check.trusted)
    }

    @Test
    fun signerReasonAndTimeAreRead() {
        val contents = cms(signedContent())
        val padded = contents + ByteArray(100)

        val check = verify(file(contents), padded, subFilter = "ETSI.CAdES.detached", reason = "Onaylıyorum")

        assertEquals(SignatureIntegrity.Intact, check.integrity)
        assertEquals("Ada Lovelace", check.signer)
        assertEquals("Test Root", check.issuer)
        assertEquals("Onaylıyorum", check.reason)
        assertTrue("signing time ${check.signedAt}", check.signedAt!! in now - day..now + day)
    }
}
