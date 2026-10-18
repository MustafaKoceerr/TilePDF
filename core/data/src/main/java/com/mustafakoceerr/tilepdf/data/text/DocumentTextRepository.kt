package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.flow.Flow

/** Text of one open document: full-text search and per-page text with positions. */
interface DocumentTextRepository {

    /**
     * Emits each page's matches as soon as that page is searched, so results
     * appear while the rest of the document is still being scanned.
     */
    fun search(uri: String, pageCount: Int, query: String): Flow<List<SearchMatch>>

    /**
     * [pageIndex]'s text with a box per character, as needed to select text.
     * Extracts the page now if it hasn't been yet.
     */
    suspend fun pageText(uri: String, pageIndex: Int): PageText

    /**
     * Opens [uri]'s text provider ahead of the first search, so any
     * background extraction can start while the user is still reading.
     */
    suspend fun prepare(uri: String)

    /** Frees the cached text of [uri], e.g. when its reader closes. */
    suspend fun release(uri: String)
}
