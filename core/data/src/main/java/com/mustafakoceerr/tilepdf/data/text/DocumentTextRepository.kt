package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.flow.Flow

/** Text of the open document: full-text search and per-page text with positions. */
interface DocumentTextRepository {
    /**
     * Emits each page's matches as soon as that page is searched, so results
     * appear while the rest of the document is still being scanned. Fails
     * with [IllegalStateException] if [document] is closed meanwhile.
     */
    fun search(document: PdfDocument, query: String): Flow<List<SearchMatch>>

    /**
     * [pageIndex]'s text with a box per character, as needed to select text.
     * Extracts the page now if it hasn't been yet.
     */
    suspend fun pageText(document: PdfDocument, pageIndex: Int): PageText

    /** Frees the cached text of [document], e.g. when its reader closes. */
    suspend fun release(document: PdfDocument)
}
