package com.mustafakoceerr.tilepdf.data.signature

import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PdfThread
import com.mustafakoceerr.tilepdf.core.pdf.Pdfium
import com.mustafakoceerr.tilepdf.core.signature.SignatureVerifier
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaver
import com.mustafakoceerr.tilepdf.data.annotation.OfflineAnnotationRepository
import com.mustafakoceerr.tilepdf.data.annotation.SafAnnotationFiles
import com.mustafakoceerr.tilepdf.data.annotation.SessionPasswords
import com.mustafakoceerr.tilepdf.data.local.TilePdfDatabase
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date

/** Signing for real: PDFium's stamp, the Kotlin signer, the platform's JCA and the file replaced. */
@RunWith(AndroidJUnit4::class)
class CertificateSigningTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, PdfThread.dispatcher)
    private val database = Room.inMemoryDatabaseBuilder(context, TilePdfDatabase::class.java).build()
    private val files = SafAnnotationFiles(context, Dispatchers.IO, pdfium)
    private val repository = OfflineAnnotationRepository(database.annotationDraftDao(), database.recognizedTextDao(), files)
    private val passwords = SessionPasswords()
    private val saver = AnnotationSaver(repository, files, passwords)
    private val verifier = SignatureVerifier { emptySet() }

    private val keys = object : SigningKeys {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val certificate: X509Certificate = JcaX509CertificateConverter().getCertificate(
            JcaX509v3CertificateBuilder(
                X500Name("CN=Ayşe Yılmaz"), BigInteger.ONE, Date(System.currentTimeMillis() - 86_400_000),
                Date(System.currentTimeMillis() + 86_400_000), X500Name("CN=Ayşe Yılmaz"), pair.public,
            ).build(JcaContentSignerBuilder("SHA256withRSA").build(pair.private)),
        )
        override fun privateKey(alias: String): PrivateKey? = pair.private.takeIf { alias == "test" }
        override fun chain(alias: String): List<X509Certificate>? = listOf(certificate).takeIf { alias == "test" }
    }

    private val signing = CertificateSigning(files, saver, repository, passwords, keys, verifier, Dispatchers.IO)

    @After
    fun tearDown() = database.close()

    private suspend fun sign(uri: String, alias: String = "test") = signing.sign(
        uri, alias, pageIndex = 0, rect = PdfRect(300f, 600f, 480f, 660f), drawing = null,
    ) { signer, _ -> listOf(signer, "19.10.2026", "Dijital olarak imzalandı") }

    private fun check(uri: String) = runBlocking {
        val bytes = context.contentResolver.openInputStream(uri.toUri())!!.use { it.readBytes() }
        pdfium.open(uri.toUri()).use { document ->
            document.signatures().map { signature ->
                verifier.verify(signature, bytes.size.toLong()) { offset, length, sink ->
                    sink(bytes.copyOfRange(offset.toInt(), (offset + length).toInt()))
                }
            }
        }
    }

    @Test
    fun signsTheDocument() = runBlocking {
        val uri = TestAssets.uriString("sample.pdf")

        assertEquals(SignResult.Signed, sign(uri))

        val checks = check(uri)
        assertEquals(1, checks.size)
        assertEquals(SignatureIntegrity.Intact, checks.single().integrity)
        assertEquals("Ayşe Yılmaz", checks.single().signer)
        assertFalse(checks.single().changedAfterSigning)
    }

    @Test
    fun signingAgainKeepsTheFirstSignatureIntact() = runBlocking {
        val uri = TestAssets.uriString("sample.pdf")
        sign(uri)

        assertEquals(SignResult.Signed, sign(uri))

        val checks = check(uri)
        assertEquals(listOf(SignatureIntegrity.Intact, SignatureIntegrity.Intact), checks.map { it.integrity })
        assertEquals(listOf(true, false), checks.map { it.changedAfterSigning })
    }

    @Test
    fun anUnknownKeyChangesNothing() = runBlocking {
        val uri = TestAssets.uriString("sample.pdf")
        val before = context.contentResolver.openInputStream(uri.toUri())!!.use { it.readBytes() }

        assertEquals(SignResult.Failed(SignFailure.KeyUnavailable), sign(uri, alias = "missing"))

        val after = context.contentResolver.openInputStream(uri.toUri())!!.use { it.readBytes() }
        assertEquals(before.toList(), after.toList())
    }
}
