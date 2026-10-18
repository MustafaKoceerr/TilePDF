package com.mustafakoceerr.tilepdf.core.signature

import org.bouncycastle.asn1.ASN1Encoding
import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Attribute
import org.bouncycastle.asn1.cms.AttributeTable
import org.bouncycastle.asn1.cms.CMSAttributes
import org.bouncycastle.asn1.ess.ESSCertIDv2
import org.bouncycastle.asn1.ess.SigningCertificateV2
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.IssuerSerial
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSAttributeTableGenerator
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate

/**
 * Makes the detached CMS of a PAdES B-B signature: signed attributes
 * content-type, message-digest and signing-certificate-v2, and no
 * signing-time, which PAdES leaves to the signature dictionary's /M. The
 * signature itself is made through the platform's JCA, so a key that never
 * leaves Android's key store signs too.
 *
 * @param chain the signing certificate first, then the ones that issued it.
 */
class CmsSigner(private val key: PrivateKey, private val chain: List<X509Certificate>) {

    fun sign(content: ByteArray): ByteArray {
        val certificate = chain.first()
        val algorithm = if (key.algorithm.equals("EC", ignoreCase = true)) "SHA256withECDSA" else "SHA256withRSA"
        val signerInfo = JcaSignerInfoGeneratorBuilder(JcaDigestCalculatorProviderBuilder().build())
            .setSignedAttributeGenerator(padesAttributes(certificate))
            .build(JcaContentSignerBuilder(algorithm).build(key), certificate)
        val generator = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(signerInfo)
            addCertificates(JcaCertStore(chain))
        }
        return generator.generate(CMSProcessableByteArray(content), false).getEncoded(ASN1Encoding.DER)
    }

    /** BouncyCastle's defaults plus signing-certificate-v2, without the signing time. */
    private fun padesAttributes(certificate: X509Certificate): CMSAttributeTableGenerator {
        val issuer = GeneralNames(GeneralName(X500Name.getInstance(certificate.issuerX500Principal.encoded)))
        val certId = ESSCertIDv2(MessageDigest.getInstance("SHA-256").digest(certificate.encoded), IssuerSerial(issuer, certificate.serialNumber))
        val attributes = ASN1EncodableVector().apply {
            add(Attribute(PKCSObjectIdentifiers.id_aa_signingCertificateV2, DERSet(SigningCertificateV2(arrayOf(certId)))))
        }
        val defaults = DefaultSignedAttributeTableGenerator(AttributeTable(attributes))
        return CMSAttributeTableGenerator { parameters -> defaults.getAttributes(parameters).remove(CMSAttributes.signingTime) }
    }
}
