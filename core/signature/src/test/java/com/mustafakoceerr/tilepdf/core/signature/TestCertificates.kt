package com.mustafakoceerr.tilepdf.core.signature

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** Keys and certificates made for a test. */
object TestCertificates {
    val day = TimeUnit.DAYS.toMillis(1)
    private val serials = AtomicLong(System.currentTimeMillis())

    fun keys(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    fun certificate(
        subject: String,
        keys: KeyPair,
        issuer: String = subject,
        signingKey: PrivateKey = keys.private,
        from: Long = System.currentTimeMillis() - day,
        to: Long = System.currentTimeMillis() + 365 * day,
        ca: Boolean = false,
    ): X509Certificate {
        val builder = JcaX509v3CertificateBuilder(
            X500Name(issuer), BigInteger.valueOf(serials.incrementAndGet()), Date(from), Date(to), X500Name(subject), keys.public,
        )
        if (ca) builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        return JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(signingKey)))
    }
}
