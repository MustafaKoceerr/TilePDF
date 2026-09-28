package com.mustafakoceerr.tilepdf.data.text

import androidx.annotation.RequiresApi
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import javax.inject.Inject

/** Provider backed by `PdfRenderer.Page.getTextContents/searchText` (API 35+). */
@RequiresApi(35)
class NativePdfTextProvider : PdfTextProvider {

    // TODO(week 4): PdfRenderer.Page.getTextContents().
    override suspend fun pageText(pageIndex: Int): PageText = PageText(pageIndex, emptyList())

    // TODO(week 4): PdfRenderer.Page.searchText() -> PageMatchBounds.
    override suspend fun search(pageIndex: Int, query: String): List<SearchMatch> = emptyList()

    override fun close() = Unit

    @RequiresApi(35)
    class Factory @Inject constructor() : PdfTextProvider.Factory {
        override suspend fun create(uri: String): PdfTextProvider = NativePdfTextProvider()
    }
}
