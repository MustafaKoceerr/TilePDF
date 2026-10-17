package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTest {

    private lateinit var database: TilePdfDatabase

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            TilePdfDatabase::class.java,
        ).build()
        database.documentDao().recordOpened("content://book", "Book.pdf", pageCount = 10, now = 1)
    }

    @After
    fun tearDown() = database.close()

    private val dao get() = database.bookmarkDao()

    @Test
    fun bookmarksAreListedInPageOrder() = runBlocking {
        dao.insert(BookmarkEntity("content://book", pageIndex = 7, createdAt = 1))
        dao.insert(BookmarkEntity("content://book", pageIndex = 2, createdAt = 2))
        assertEquals(listOf(2, 7), dao.observe("content://book").first().map { it.pageIndex })
    }

    @Test
    fun addingTheSamePageTwiceKeepsOne() = runBlocking {
        dao.insert(BookmarkEntity("content://book", pageIndex = 3, createdAt = 1))
        dao.insert(BookmarkEntity("content://book", pageIndex = 3, createdAt = 2))
        assertEquals(1, dao.observe("content://book").first().size)
    }

    @Test
    fun removingADocumentRemovesItsBookmarks() = runBlocking {
        dao.insert(BookmarkEntity("content://book", pageIndex = 3, createdAt = 1))
        database.documentDao().delete("content://book")
        assertTrue(dao.observe("content://book").first().isEmpty())
    }

    @Test
    fun deleteRemovesOnlyThatPage() = runBlocking {
        dao.insert(BookmarkEntity("content://book", pageIndex = 3, createdAt = 1))
        dao.insert(BookmarkEntity("content://book", pageIndex = 4, createdAt = 1))
        dao.delete("content://book", pageIndex = 3)
        assertEquals(listOf(4), dao.observe("content://book").first().map { it.pageIndex })
    }
}
