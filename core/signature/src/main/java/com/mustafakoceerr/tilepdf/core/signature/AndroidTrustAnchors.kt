package com.mustafakoceerr.tilepdf.core.signature

import java.security.KeyStore
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate

/**
 * The roots Android trusts: the system's certificate authorities and the
 * ones the user added in Settings. None when the store can't be read.
 */
object AndroidTrustAnchors {
    fun load(): Set<TrustAnchor> = try {
        val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
        store.aliases().toList().mapNotNull { alias -> (store.getCertificate(alias) as? X509Certificate)?.let { TrustAnchor(it, null) } }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}
