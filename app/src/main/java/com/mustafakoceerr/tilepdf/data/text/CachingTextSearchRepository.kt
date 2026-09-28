package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class CachingTextSearchRepository @Inject constructor(
    private val providerFactory: PdfTextProvider.Factory,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TextSearchRepository {

    // TODO(week 3): keep the provider and extracted PageText per document
    //  instead of recreating them for every query.
    override fun search(uri: String, pageCount: Int, query: String): Flow<SearchMatch> = flow {
        if (query.isBlank()) return@flow
        providerFactory.create(uri).use { provider ->
            for (pageIndex in 0 until pageCount) {
                provider.search(pageIndex, query).forEach { emit(it) }
            }
        }
    }.flowOn(ioDispatcher)
}
