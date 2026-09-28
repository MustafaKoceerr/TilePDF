package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.flow.Flow

/** Full-text search over one document, with extracted text cached per page. */
interface TextSearchRepository {

    /**
     * Emits each page's matches as soon as that page is searched, so results
     * appear while the rest of the document is still being scanned.
     */
    fun search(uri: String, pageCount: Int, query: String): Flow<List<SearchMatch>>

    /** Frees the cached text of [uri], e.g. when its reader closes. */
    suspend fun release(uri: String)
}
