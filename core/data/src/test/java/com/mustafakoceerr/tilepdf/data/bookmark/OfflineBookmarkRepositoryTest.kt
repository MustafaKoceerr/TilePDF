package com.mustafakoceerr.tilepdf.data.bookmark

import com.mustafakoceerr.tilepdf.data.local.BookmarkDao
import com.mustafakoceerr.tilepdf.data.local.BookmarkEntity
import com.mustafakoceerr.tilepdf.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class OfflineBookmarkRepositoryTest {

    private val dao = FakeBookmarkDao()
    private val repository = OfflineBookmarkRepository(dao, now = { 42L })

    @Test
    fun toggleAddsThenRemoves() = runTest {
        repository.toggle("content://book", pageIndex = 4)
        assertEquals(listOf(Bookmark(4)), repository.observe("content://book", pageCount = 10).first())
        repository.toggle("content://book", pageIndex = 4)
        assertEquals(emptyList<Bookmark>(), repository.observe("content://book", pageCount = 10).first())
    }

    @Test
    fun pagesBeyondTheDocumentAreDropped() = runTest {
        // The file changed and lost pages since they were bookmarked.
        dao.insert(BookmarkEntity("content://book", pageIndex = 2, createdAt = 1))
        dao.insert(BookmarkEntity("content://book", pageIndex = 12, createdAt = 1))
        assertEquals(listOf(Bookmark(2)), repository.observe("content://book", pageCount = 10).first())
    }

    private class FakeBookmarkDao : BookmarkDao {
        private val rows = MutableStateFlow<List<BookmarkEntity>>(emptyList())
        override fun observe(uri: String): Flow<List<BookmarkEntity>> =
            rows.map { all -> all.filter { it.uri == uri }.sortedBy { it.pageIndex } }
        override suspend fun insert(bookmark: BookmarkEntity) {
            if (rows.value.none { it.uri == bookmark.uri && it.pageIndex == bookmark.pageIndex }) rows.value += bookmark
        }
        override suspend fun delete(uri: String, pageIndex: Int) {
            rows.value = rows.value.filterNot { it.uri == uri && it.pageIndex == pageIndex }
        }
        override suspend fun exists(uri: String, pageIndex: Int): Boolean =
            rows.value.any { it.uri == uri && it.pageIndex == pageIndex }
    }
}
