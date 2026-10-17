package com.mustafakoceerr.tilepdf.data.bookmark

import com.mustafakoceerr.tilepdf.data.local.BookmarkDao
import com.mustafakoceerr.tilepdf.data.local.BookmarkEntity
import com.mustafakoceerr.tilepdf.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineBookmarkRepository internal constructor(
    private val dao: BookmarkDao,
    private val now: () -> Long,
) : BookmarkRepository {

    @Inject
    constructor(dao: BookmarkDao) : this(dao, System::currentTimeMillis)

    override fun observe(uri: String, pageCount: Int): Flow<List<Bookmark>> =
        dao.observe(uri).map { rows -> rows.filter { it.pageIndex < pageCount }.map { Bookmark(it.pageIndex) } }

    override suspend fun toggle(uri: String, pageIndex: Int) {
        if (dao.exists(uri, pageIndex)) dao.delete(uri, pageIndex)
        else dao.insert(BookmarkEntity(uri, pageIndex, createdAt = now()))
    }
}
