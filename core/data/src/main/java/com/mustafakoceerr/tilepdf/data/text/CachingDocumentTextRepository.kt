package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps one document's text provider open, and with it every page's
 * extracted text: the first search pays for extraction, later ones only scan
 * memory. Opening another document replaces it, since only one reader is
 * shown at a time.
 */
@Singleton
class CachingDocumentTextRepository @Inject constructor(
    private val providerFactory: PdfTextProvider.Factory,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DocumentTextRepository {

    private val mutex = Mutex()
    private var current: Pair<String, PdfTextProvider>? = null

    override fun search(uri: String, pageCount: Int, query: String): Flow<List<SearchMatch>> = flow {
        if (query.isBlank()) return@flow
        val provider = provider(uri)
        for (pageIndex in 0 until pageCount) {
            val matches = provider.search(pageIndex, query)
            if (matches.isNotEmpty()) emit(matches)
        }
    }.flowOn(ioDispatcher)

    override suspend fun pageText(uri: String, pageIndex: Int): PageText =
        withContext(ioDispatcher) { provider(uri).pageText(pageIndex) }

    override suspend fun prepare(uri: String) {
        provider(uri)
    }

    override suspend fun release(uri: String) = mutex.withLock {
        val (openUri, provider) = current ?: return@withLock
        if (openUri == uri) {
            current = null
            provider.close()
        }
    }

    private suspend fun provider(uri: String): PdfTextProvider = mutex.withLock {
        current?.let { (openUri, provider) -> if (openUri == uri) return@withLock provider }
        current?.second?.close()
        providerFactory.create(uri).also { current = uri to it }
    }
}
