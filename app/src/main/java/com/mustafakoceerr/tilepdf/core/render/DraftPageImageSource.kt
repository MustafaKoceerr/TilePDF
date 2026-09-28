package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument

/** Budget for low-resolution whole-page drafts. */
private const val DRAFT_CACHE_BYTES = 24 * 1024 * 1024

/** Whole-page renders kept in a byte-bounded LRU cache. */
class DraftPageImageSource(
    private val document: PdfDocument,
    private val pageRenderer: PageRenderer,
) : PageImageSource {

    private val cache = BitmapCache<Int>(DRAFT_CACHE_BYTES)

    override fun cached(pageIndex: Int, widthPx: Int): Bitmap? =
        cache.get(pageIndex)?.takeIf { it.width == widthPx }

    override suspend fun load(pageIndex: Int, widthPx: Int): Bitmap =
        cached(pageIndex, widthPx)
            ?: pageRenderer.render(document, pageIndex, widthPx).also { cache.put(pageIndex, it) }
}
