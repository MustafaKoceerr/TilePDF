package com.mustafakoceerr.tilepdf.data.signature

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Signature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SignatureStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val store by lazy { SignatureStore(tmp.root.resolve("signature.json"), Dispatchers.Unconfined) }
    private val signature = Signature(listOf(listOf(PdfPoint(0f, 0f), PdfPoint(1f, 0.25f)), listOf(PdfPoint(0.5f, 0.1f))), 0.25f)

    @Test
    fun savedSignatureLoads() = runTest {
        store.save(signature)

        assertEquals(signature, store.load())
    }

    @Test
    fun nothingSavedLoadsNothing() = runTest {
        assertNull(store.load())
    }

    @Test
    fun clearForgetsIt() = runTest {
        store.save(signature)

        store.clear()

        assertNull(store.load())
    }

    @Test
    fun damagedFileLoadsNothing() = runTest {
        tmp.root.resolve("signature.json").writeText("{not json")

        assertNull(store.load())
    }
}
