package com.mustafakoceerr.tilepdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningTest {

    private val all = mapOf(
        "TILEPDF_UPLOAD_KEYSTORE" to "/keys/upload.jks",
        "TILEPDF_UPLOAD_KEYSTORE_PASSWORD" to "store-secret",
        "TILEPDF_UPLOAD_KEY_ALIAS" to "upload-alias",
        "TILEPDF_UPLOAD_KEY_PASSWORD" to "key-secret",
    )

    @Test
    fun allFourSignRelease() {
        assertEquals(
            ReleaseSigning.Configured("/keys/upload.jks", "store-secret", "upload-alias", "key-secret"),
            releaseSigning(all::get),
        )
    }

    @Test
    fun noneLeavesUnsigned() = assertEquals(ReleaseSigning.Unsigned, releaseSigning { null })

    @Test
    fun blankCountsAsMissing() {
        val env = all + ("TILEPDF_UPLOAD_KEYSTORE_PASSWORD" to " ")
        assertEquals(ReleaseSigning.Incomplete(listOf("TILEPDF_UPLOAD_KEYSTORE_PASSWORD")), releaseSigning(env::get))
    }

    @Test
    fun partialEnvFailsNamingMissing() {
        val env = all.filterKeys { it == "TILEPDF_UPLOAD_KEYSTORE" || it == "TILEPDF_UPLOAD_KEY_ALIAS" }
        val result = releaseSigning(env::get) as ReleaseSigning.Incomplete
        assertEquals(listOf("TILEPDF_UPLOAD_KEYSTORE_PASSWORD", "TILEPDF_UPLOAD_KEY_PASSWORD"), result.missing)
        assertTrue(result.message.contains("TILEPDF_UPLOAD_KEYSTORE_PASSWORD"))
        assertTrue(result.message.contains("TILEPDF_UPLOAD_KEY_PASSWORD"))
        // The values that are set never appear in the message.
        assertFalse(result.message.contains("/keys/upload.jks"))
        assertFalse(result.message.contains("upload-alias"))
    }

    @Test
    fun configuredHidesSecrets() {
        val text = releaseSigning(all::get).toString()
        assertFalse(text.contains("store-secret"))
        assertFalse(text.contains("key-secret"))
        assertFalse(text.contains("upload-alias"))
    }
}
