package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import java.io.Closeable

/**
 * Extracts text and its positions from one document. Implementations differ
 * only in where the text comes from; all return PDF point-space boxes, so the
 * rest of the app never knows which one is in use.
 */
interface PdfTextProvider : Closeable {

    suspend fun pageText(pageIndex: Int): PageText

    /** Matches on one page; implementations may use a faster native search. */
    suspend fun search(pageIndex: Int, query: String): List<SearchMatch>

    /** Creates a provider for one document. */
    fun interface Factory {
        suspend fun create(uri: String): PdfTextProvider
    }
}
