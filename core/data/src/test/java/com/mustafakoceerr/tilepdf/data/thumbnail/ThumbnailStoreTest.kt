package com.mustafakoceerr.tilepdf.data.thumbnail

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ThumbnailStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val uri = "content://com.android.providers/document/Ödev çalışması.pdf"
    private val store by lazy { ThumbnailStore(tmp.root.resolve("thumbnails"), Dispatchers.Unconfined) }

    @Test
    fun savedFileIsFound() = runTest {
        store.save(uri, byteArrayOf(1, 2, 3))

        val found = store.find(listOf(uri, "content://other")).getValue(uri)

        assertArrayEquals(byteArrayOf(1, 2, 3), found.file.readBytes())
        assertEquals(setOf(uri), store.find(listOf(uri, "content://other")).keys)
    }

    @Test
    fun saveReplacesOldThumbnail() = runTest {
        store.save(uri, byteArrayOf(1))
        store.save(uri, byteArrayOf(2))

        assertArrayEquals(byteArrayOf(2), store.find(listOf(uri)).getValue(uri).file.readBytes())
    }

    @Test
    fun deleteRemovesIt() = runTest {
        store.save(uri, byteArrayOf(1))

        store.delete(uri)

        assertEquals(emptyMap<String, Thumbnail>(), store.find(listOf(uri)))
    }

    @Test
    fun keysAreStableAndFileNameSafe() = runTest {
        store.save(uri, byteArrayOf(1))
        val name = store.find(listOf(uri)).getValue(uri).file.name

        assertTrue(name, name.matches(Regex("""[0-9a-f]{64}\.png""")))
        assertEquals(name, ThumbnailStore(tmp.root.resolve("thumbnails"), Dispatchers.Unconfined).find(listOf(uri)).getValue(uri).file.name)
    }

    @Test
    fun emptyStoreFindsNothing() = runTest {
        assertEquals(emptyMap<String, Thumbnail>(), store.find(listOf(uri)))
    }
}
