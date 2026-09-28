package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.flow.Flow

/** Full-text search over one document, with extracted text cached per page. */
interface TextSearchRepository {

    /** Emits matches page by page, so results appear while the search runs. */
    fun search(uri: String, pageCount: Int, query: String): Flow<SearchMatch>
}
