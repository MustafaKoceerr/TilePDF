package com.mustafakoceerr.tilepdf.data.text

import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import javax.inject.Inject

/**
 * Universal provider backed by PdfBox-Android; works on every supported API
 * level. Default choice below API 35.
 */
class PdfBoxTextProvider : PdfTextProvider {

    // TODO(week 3): PDDocument + PDFTextStripper collecting TextPosition boxes.
    override suspend fun pageText(pageIndex: Int): PageText = PageText(pageIndex, emptyList())

    // TODO(week 3): search the extracted text with a shared TextSearcher.
    override suspend fun search(pageIndex: Int, query: String): List<SearchMatch> = emptyList()

    override fun close() = Unit

    class Factory @Inject constructor() : PdfTextProvider.Factory {
        override suspend fun create(uri: String): PdfTextProvider = PdfBoxTextProvider()
    }
}
