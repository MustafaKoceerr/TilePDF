package com.mustafakoceerr.tilepdf.data.signature

import android.content.Context
import android.security.KeyChain
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PdfPasswordException
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfEncryptedException
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfFormatException
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfSignatureTooLargeException
import com.mustafakoceerr.tilepdf.core.pdfwriter.PdfSigner
import com.mustafakoceerr.tilepdf.core.signature.CmsSigner
import com.mustafakoceerr.tilepdf.core.signature.SignatureVerifier
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationFiles
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationRepository
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaving
import com.mustafakoceerr.tilepdf.data.annotation.SaveResult
import com.mustafakoceerr.tilepdf.data.annotation.SessionPasswords
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.SaveError
import com.mustafakoceerr.tilepdf.model.Signature
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.time.ZonedDateTime
import java.util.UUID
import javax.inject.Inject

/** A key and its certificates from Android's certificate store, by alias. */
interface SigningKeys {
    /** Null when the key is gone or the user didn't let TilePDF use it. */
    fun privateKey(alias: String): PrivateKey?

    /** The signing certificate first, then its issuers; null like [privateKey]. */
    fun chain(alias: String): List<X509Certificate>?
}

/** Android's KeyChain. Its calls block, so they're made off the main thread. */
class KeyChainSigningKeys @Inject constructor(@param:ApplicationContext private val context: Context) : SigningKeys {
    override fun privateKey(alias: String): PrivateKey? = try {
        KeyChain.getPrivateKey(context, alias)
    } catch (_: Exception) {
        null
    }

    override fun chain(alias: String): List<X509Certificate>? = try {
        KeyChain.getCertificateChain(context, alias)?.toList()?.takeIf { it.isNotEmpty() }
    } catch (_: Exception) {
        null
    }
}

sealed interface SignResult {
    data object Signed : SignResult
    data class Failed(val reason: SignFailure) : SignResult
}

enum class SignFailure {
    /** The key is gone, or the user didn't let TilePDF use it. */
    KeyUnavailable,

    /** TilePDF can't write to the file; a copy can be signed instead. */
    NoWritePermission,

    /** An encrypted document, which this signer can't write to. */
    Encrypted,

    Failed,
}

/**
 * Signs a document with a certificate from Android's store: its unsaved
 * changes are saved first, so the signature covers them; a signature box
 * goes where the user chose; the signed file is checked before it replaces
 * the document. Nothing is kept: no key, no password, no alias.
 */
class CertificateSigning @Inject constructor(
    private val files: AnnotationFiles,
    private val saver: AnnotationSaving,
    private val repository: AnnotationRepository,
    private val passwords: SessionPasswords,
    private val keys: SigningKeys,
    private val verifier: SignatureVerifier,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Signs [uri] with the key [alias] in a box at [rect] on [pageIndex]
     * (top-left points), showing [drawing] if given and the [lines] made
     * from the signer's name and the signing time.
     */
    suspend fun sign(
        uri: String,
        alias: String,
        pageIndex: Int,
        rect: PdfRect,
        drawing: Signature?,
        lines: (signer: String, at: ZonedDateTime) -> List<String>,
    ): SignResult = withContext(ioDispatcher) {
        if (passwords.get(uri) != null) return@withContext SignResult.Failed(SignFailure.Encrypted)
        if (repository.observe(uri).first()?.dirty == true) {
            when (val saved = saver.save(uri)) {
                is SaveResult.Failed -> return@withContext SignResult.Failed(
                    if (saved.error == SaveError.NoWritePermission) SignFailure.NoWritePermission else SignFailure.Failed,
                )
                else -> Unit
            }
        }
        val chain = keys.chain(alias) ?: return@withContext SignResult.Failed(SignFailure.KeyUnavailable)
        val key = keys.privateKey(alias) ?: return@withContext SignResult.Failed(SignFailure.KeyUnavailable)
        val now = ZonedDateTime.now()
        // Not "tilepdf-": that prefix marks TilePDF's annotations, which the reader hides and the draft owns.
        val stampName = "signature-${UUID.randomUUID()}"
        val stamped = files.newTempFile()
        val signed = files.newTempFile()
        try {
            files.openForEditing(uri, null).use { document ->
                document.addSignatureStamp(pageIndex, stampName, signatureAppearance(rect, lines(commonName(chain.first()), now), drawing))
                document.saveIncremental(stamped)
            }
            val bytes = PdfSigner().sign(stamped.readBytes(), stampName, now, reason = "", sign = CmsSigner(key, chain)::sign)
            signed.writeBytes(bytes)
            if (!isIntact(bytes, signed)) return@withContext SignResult.Failed(SignFailure.Failed)
            try {
                files.replace(uri, signed)
            } catch (_: SecurityException) {
                return@withContext SignResult.Failed(SignFailure.NoWritePermission)
            } catch (_: FileNotFoundException) {
                return@withContext SignResult.Failed(SignFailure.NoWritePermission)
            } catch (_: UnsupportedOperationException) {
                return@withContext SignResult.Failed(SignFailure.NoWritePermission)
            }
            // The file has a new version; the draft, already saved, is based on it now.
            repository.observe(uri).first()?.let { draft -> files.stamp(uri)?.let { repository.markSaved(uri, draft, it) } }
            SignResult.Signed
        } catch (e: CancellationException) {
            throw e
        } catch (_: PdfEncryptedException) {
            SignResult.Failed(SignFailure.Encrypted)
        } catch (_: PdfPasswordException) {
            SignResult.Failed(SignFailure.Encrypted)
        } catch (_: PdfFormatException) {
            SignResult.Failed(SignFailure.Failed)
        } catch (_: PdfSignatureTooLargeException) {
            SignResult.Failed(SignFailure.Failed)
        } catch (_: GeneralSecurityException) {
            SignResult.Failed(SignFailure.KeyUnavailable)
        } catch (_: IOException) {
            SignResult.Failed(SignFailure.Failed)
        } catch (_: Exception) {
            // BouncyCastle's operator and CMS exceptions: the key couldn't sign.
            SignResult.Failed(SignFailure.Failed)
        } finally {
            stamped.delete()
            signed.delete()
        }
    }

    /** Whether the newest signature of the signed file checks out, read back as any reader would. */
    private suspend fun isIntact(bytes: ByteArray, file: java.io.File): Boolean {
        val signature = files.openForReading(file, null).use { it.signatures() }.lastOrNull() ?: return false
        val check = verifier.verify(signature, bytes.size.toLong()) { offset, length, sink ->
            sink(bytes.copyOfRange(offset.toInt(), (offset + length).toInt()))
        }
        return check.integrity == SignatureIntegrity.Intact
    }

    private fun commonName(certificate: X509Certificate): String {
        val name = X500Name.getInstance(certificate.subjectX500Principal.encoded)
        return name.getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.toString() ?: certificate.subjectX500Principal.name
    }
}
