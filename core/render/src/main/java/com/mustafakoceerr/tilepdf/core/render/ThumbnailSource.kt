package com.mustafakoceerr.tilepdf.core.render

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument

/** Budget for the page grid; a 112 dp A4 thumbnail is about 0.3 MB. */
private const val THUMBNAIL_CACHE_BYTES = 16 * 1024 * 1024

/**
 * Small whole-page images for the page grid of one open document, cached by
 * page. Evicted bitmaps are left to the garbage collector, since the grid
 * may still be drawing them.
 */
class ThumbnailSource(
    private val document: PdfDocument,
    private val pageRenderer: PageRenderer,
    cacheBytes: Int = THUMBNAIL_CACHE_BYTES,
    /** How each page is shown when turned or cropped; a change needs a new source. */
    private val transforms: List<PageViewTransform> = emptyList(),
) {
    private val cache = BitmapCache<Int>(cacheBytes)

    /** [pageIndex] rendered [widthPx] wide. */
    suspend fun thumbnail(pageIndex: Int, widthPx: Int): ImageBitmap {
        val bitmap = cache.get(pageIndex)?.takeIf { it.width == widthPx }
            ?: pageRenderer.renderPage(document, pageIndex, widthPx, transforms.getOrNull(pageIndex)).also { cache.put(pageIndex, it) }
        return bitmap.asImageBitmap()
    }

    fun trimMemory() = cache.evictAll()

    /** Drops [pageIndex]'s thumbnail: the page changed. */
    fun invalidate(pageIndex: Int) {
        cache.remove(pageIndex)
    }
}
