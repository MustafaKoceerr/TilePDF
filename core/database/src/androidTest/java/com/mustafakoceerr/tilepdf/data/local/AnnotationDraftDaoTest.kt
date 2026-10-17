package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationDraftDaoTest {

    private lateinit var database: TilePdfDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TilePdfDatabase::class.java,
        ).build()
        database.documentDao().recordOpened("content://book", "Book.pdf", pageCount = 10, now = 1)
        database.documentDao().recordOpened("content://other", "Other.pdf", pageCount = 3, now = 1)
    }

    @After
    fun tearDown() = database.close()

    private val dao get() = database.annotationDraftDao()

    private fun draft(uri: String = "content://book", annotations: String = "[]", dirty: Boolean = false) =
        AnnotationDraftEntity(
            uri = uri, annotations = annotations, removedForeign = "[]", dirty = dirty,
            baseSize = 100, baseModified = 5, saveError = null, updatedAt = 1,
        )

    @Test
    fun upsertAndObserve() = runBlocking {
        dao.upsert(draft(annotations = "[1]"))
        dao.upsert(draft(annotations = "[2]"))
        assertEquals("[2]", dao.observe("content://book").first()!!.annotations)
        assertEquals("[2]", dao.get("content://book")!!.annotations)
    }

    @Test
    fun deletingDocumentDeletesDraft() = runBlocking {
        dao.upsert(draft())
        database.documentDao().delete("content://book")
        assertNull(dao.get("content://book"))
    }

    @Test
    fun dirtyUrisListsOnlyDirty() = runBlocking {
        dao.upsert(draft(dirty = true))
        dao.upsert(draft(uri = "content://other", dirty = false))
        assertEquals(listOf("content://book"), dao.dirtyUris())
    }

    @Test
    fun updateIsAtomic() = runBlocking {
        dao.upsert(draft(annotations = ""))
        (1..20).map { i ->
            async(Dispatchers.IO) { dao.update("content://book") { it.copy(annotations = it.annotations + "x") } }
        }.awaitAll()
        assertEquals("x".repeat(20), dao.get("content://book")!!.annotations)
    }

    @Test
    fun updateWithNullWritesNothing() = runBlocking {
        dao.upsert(draft(annotations = "[1]"))
        assertNull(dao.update("content://book") { null })
        assertEquals("[1]", dao.get("content://book")!!.annotations)
    }
}
