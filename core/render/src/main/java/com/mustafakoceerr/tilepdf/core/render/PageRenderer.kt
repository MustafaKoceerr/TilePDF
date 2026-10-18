package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Renders whole pages or single tiles of a page into bitmaps. */
class PageRenderer @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The whole page, scaled to [targetWidthPx]. */
    suspend fun renderPage(document: PdfDocument, pageIndex: Int, targetWidthPx: Int): Bitmap {
        val pageSize = document.pageSizes[pageIndex]
        val scale = pageSize.scaleForWidth(targetWidthPx)
        val bitmap = whiteBitmap(targetWidthPx, pageSize.heightForWidth(targetWidthPx))
        document.render(pageIndex, bitmap, Matrix().apply { setScale(scale, scale) })
        return bitmap
    }

    /**
     * Only the part of the page covered by [tile]. The page is scaled to the
     * tile's render width, then shifted so the tile's top-left corner lands
     * at the bitmap origin; the engine clips everything else.
     *
     * @param reuse a pooled bitmap to render into instead of allocating; it is
     *   reconfigured to the tile size, so it must be large enough.
     */
    suspend fun renderTile(document: PdfDocument, tile: Tile, reuse: Bitmap? = null): Bitmap {
        val key = tile.key
        val scale = document.pageSizes[key.pageIndex].scaleForWidth(key.renderWidth)
        val bitmap = whiteBitmap(tile.bounds.width, tile.bounds.height, reuse)
        val transform = Matrix().apply {
            setScale(scale, scale)
            postTranslate(-tile.bounds.left.toFloat(), -tile.bounds.top.toFloat())
        }
        document.render(key.pageIndex, bitmap, transform)
        return bitmap
    }

    // PDF pages have a transparent background; paint it white first.
    private suspend fun whiteBitmap(width: Int, height: Int, reuse: Bitmap? = null): Bitmap =
        withContext(ioDispatcher) {
            val bitmap = reuse?.apply { reconfigure(width, height, Bitmap.Config.ARGB_8888) }
                ?: createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.apply { eraseColor(Color.WHITE) }
        }
}
