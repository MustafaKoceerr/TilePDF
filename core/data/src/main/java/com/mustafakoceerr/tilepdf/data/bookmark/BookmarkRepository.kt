package com.mustafakoceerr.tilepdf.data.bookmark

import com.mustafakoceerr.tilepdf.model.Bookmark
import kotlinx.coroutines.flow.Flow

/** Pages the user bookmarked, per document. */
interface BookmarkRepository {
    /** In page order; pages at or beyond [pageCount] (a changed file) are left out. */
    fun observe(uri: String, pageCount: Int): Flow<List<Bookmark>>

    /** Bookmarks [pageIndex], or removes the bookmark if it is already there. */
    suspend fun toggle(uri: String, pageIndex: Int)
}
