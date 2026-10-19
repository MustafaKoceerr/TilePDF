package com.mustafakoceerr.tilepdf.core.signature

import com.mustafakoceerr.tilepdf.core.pdf.PdfSignature
import com.mustafakoceerr.tilepdf.model.SignatureCheck
import com.mustafakoceerr.tilepdf.model.SignatureIntegrity
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.BCStyle
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.SignerInformation
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import java.security.MessageDigest
import java.security.cert.CertPathBuilder
import java.security.cert.CertStore
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Date

/** Reads [length] bytes of the signed file from [offset], handing them to [sink] in pieces. */
fun interface SignedBytes {
    fun read(offset: Long, length: Long, sink: (ByteArray) -> Unit)
}

/** The signatures TilePDF checks: a detached CMS over the byte ranges. */
private val DETACHED_SUB_FILTERS = setOf("adbe.pkcs7.detached", "ETSI.CAdES.detached")

/** Digest algorithms by OID, in the names every JCA knows. */
private val DIGESTS = mapOf(
    "1.3.14.3.2.26" to "SHA-1",
    "2.16.840.1.101.3.4.2.1" to "SHA-256",
    "2.16.840.1.101.3.4.2.2" to "SHA-384",
    "2.16.840.1.101.3.4.2.3" to "SHA-512",
)

/**
 * Checks PDF signatures. BouncyCastle only parses the CMS: the signature
 * and the certificate chain are checked with the platform's JCA, so no
 * provider is registered. Revocation isn't checked, as TilePDF is offline.
 *
 * @param anchors the certificates a chain may end in.
 */
class SignatureVerifier(private val anchors: () -> Set<TrustAnchor>) {

    fun verify(signature: PdfSignature, fileLength: Long, bytes: SignedBytes): SignatureCheck {
        val ranges = signature.byteRange.chunked(2).filter { it.size == 2 }
        val unchecked = SignatureCheck(
            signer = "",
            issuer = "",
            signedAt = signature.signedAt,
            reason = signature.reason,
            integrity = SignatureIntegrity.Broken,
            trusted = false,
            changedAfterSigning = ranges.lastOrNull()?.let { (start, length) -> start + length < fileLength } ?: false,
        )
        if (signature.subFilter !in DETACHED_SUB_FILTERS) return unchecked.copy(integrity = SignatureIntegrity.Unsupported)
        return try {
            // Only the first object: the signature's hole in the file is padded with zeros.
            val content = ContentInfo.getInstance(ASN1InputStream(signature.contents).readObject())
            val parsed = CMSSignedData(content)
            val algorithm = parsed.signerInfos.signers.first().digestAlgOID
            val digestName = DIGESTS[algorithm] ?: return unchecked.copy(integrity = SignatureIntegrity.Unsupported)
            val digest = MessageDigest.getInstance(digestName)
            for ((start, length) in ranges) bytes.read(start, length) { digest.update(it) }
            // The signed data with the digest given, so the file needn't be in memory.
            val signed = CMSSignedData(mapOf(ASN1ObjectIdentifier(algorithm) to digest.digest()), content)
            val signer = signed.signerInfos.signers.first()
            val certificates = signed.certificates.getMatches(null).map { JcaX509CertificateConverter().getCertificate(it as X509CertificateHolder) }
            @Suppress("UNCHECKED_CAST")
            val holder = signed.certificates.getMatches(signer.sid as org.bouncycastle.util.Selector<X509CertificateHolder>).firstOrNull()
                ?: return unchecked
            val certificate = JcaX509CertificateConverter().getCertificate(holder)
            // With the key alone: a certificate that expired is a matter of trust, not of intact bytes.
            val intact = signer.verify(JcaSimpleSignerInfoVerifierBuilder().build(certificate.publicKey))
            val signedAt = signingTime(signer) ?: signature.signedAt
            unchecked.copy(
                signer = commonName(holder.subject),
                issuer = commonName(holder.issuer),
                signedAt = signedAt,
                integrity = if (intact) SignatureIntegrity.Intact else SignatureIntegrity.Broken,
                trusted = intact && chainsToAnchor(certificate, certificates, signedAt),
            )
        } catch (_: Exception) {
            unchecked
        }
    }

    /** Whether [certificate] chains to an anchor through [others], valid at [at] (now when unknown). */
    private fun chainsToAnchor(certificate: X509Certificate, others: List<X509Certificate>, at: Long?): Boolean = try {
        val parameters = PKIXBuilderParameters(anchors(), X509CertSelector().apply { this.certificate = certificate }).apply {
            isRevocationEnabled = false
            date = Date(at ?: System.currentTimeMillis())
            addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(others)))
        }
        CertPathBuilder.getInstance("PKIX").build(parameters)
        true
    } catch (_: Exception) {
        false
    }

    /** The signing time the signer put among the signed attributes, if any. */
    private fun signingTime(signer: SignerInformation): Long? {
        val attribute: Attribute = signer.signedAttributes?.get(CMSAttributes.signingTime) ?: return null
        return Time.getInstance(attribute.attrValues.getObjectAt(0)).date.time
    }

    private fun commonName(name: X500Name): String =
        name.getRDNs(BCStyle.CN).firstOrNull()?.first?.value?.toString() ?: name.toString()
}
