package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch

/**
 * Extracts text and its positions from one document. Implementations differ
 * only in where the text comes from; all return PDF point-space boxes, so the
 * rest of the app never knows which one is in use.
 */
interface PdfTextProvider {

    /**
     * Extracted once per page, then served from memory. Has a box per
     * character whenever the provider can supply one.
     */
    suspend fun pageText(pageIndex: Int): PageText

    /** Matches on one page; implementations may use a faster native search. */
    suspend fun search(pageIndex: Int, query: String): List<SearchMatch>

    /** Waits for in-flight work, then releases the document. */
    suspend fun close()

    /** Opens a provider for one document. */
    fun interface Factory {
        /** @throws java.io.IOException if the document can't be read. */
        suspend fun create(uri: String): PdfTextProvider
    }
}
