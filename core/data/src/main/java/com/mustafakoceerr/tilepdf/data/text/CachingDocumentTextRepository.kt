package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.text.TextSearcher
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the extracted text of one document: the first search extracts each
 * page as it reaches it, later searches only scan memory. Another document
 * replaces the cache, since only one reader is shown at a time.
 */
@Singleton
class CachingDocumentTextRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentTextRepository {

    private val mutex = Mutex()
    private var document: PdfDocument? = null
    private var pages = HashMap<Int, PageText>()

    override fun search(document: PdfDocument, query: String): Flow<List<SearchMatch>> = flow {
        if (query.isBlank()) return@flow
        for (pageIndex in 0 until document.pageCount) {
            val matches = TextSearcher.search(pageText(document, pageIndex), query)
            if (matches.isNotEmpty()) emit(matches)
        }
    }.flowOn(ioDispatcher)

    override suspend fun pageText(document: PdfDocument, pageIndex: Int): PageText {
        mutex.withLock { cacheFor(document)[pageIndex] }?.let { return it }
        // Extracted outside the lock: it waits on the PDF dispatcher.
        val text = document.pageText(pageIndex)
        mutex.withLock { if (this.document === document) pages[pageIndex] = text }
        return text
    }

    override suspend fun forget(document: PdfDocument, pageIndex: Int) {
        mutex.withLock { if (this.document === document) pages.remove(pageIndex) }
    }

    override suspend fun release(document: PdfDocument) = mutex.withLock {
        if (this.document === document) {
            this.document = null
            pages = HashMap()
        }
    }

    /** The cache for [document], emptied first if it held another document's text. */
    private fun cacheFor(document: PdfDocument): HashMap<Int, PageText> {
        if (this.document !== document) {
            this.document = document
            pages = HashMap()
        }
        return pages
    }
}
