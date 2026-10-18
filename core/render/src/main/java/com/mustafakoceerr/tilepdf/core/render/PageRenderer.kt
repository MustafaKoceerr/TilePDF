package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import androidx.core.graphics.createBitmap
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/** Wide enough to find margins within a point or two on a typical page. */
private const val CROP_SAMPLE_WIDTH_PX = 300

/** Room left around the content when margins are cropped. */
private const val CROP_PADDING_PT = 8f

/** Renders whole pages or single tiles of a page into bitmaps. */
class PageRenderer @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The whole page, scaled to [targetWidthPx]; shown through [transform] when it's turned or cropped. */
    suspend fun renderPage(
        document: PdfDocument,
        pageIndex: Int,
        targetWidthPx: Int,
        transform: PageViewTransform? = null,
    ): Bitmap {
        val pageSize = transform?.size ?: document.pageSizes[pageIndex]
        val scale = pageSize.scaleForWidth(targetWidthPx)
        val bitmap = whiteBitmap(targetWidthPx, pageSize.heightForWidth(targetWidthPx))
        document.render(pageIndex, bitmap, viewMatrix(transform, scale))
        return bitmap
    }

    /**
     * Where [pageIndex]'s content is, for cropping its margins: a small render
     * searched for ink. Null when there's nothing worth cropping.
     */
    suspend fun marginCrop(document: PdfDocument, pageIndex: Int): PdfRect? {
        val sample = renderPage(document, pageIndex, CROP_SAMPLE_WIDTH_PX)
        return withContext(ioDispatcher) {
            try {
                val pixels = IntArray(sample.width * sample.height)
                sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
                marginCrop(inkBounds(pixels, sample.width, sample.height), sample.width, document.pageSizes[pageIndex], CROP_PADDING_PT)
            } finally {
                sample.recycle()
            }
        }
    }

    /** The whole page as a PNG [targetWidthPx] wide, e.g. for the library. */
    suspend fun renderPagePng(document: PdfDocument, pageIndex: Int, targetWidthPx: Int): ByteArray {
        val bitmap = renderPage(document, pageIndex, targetWidthPx)
        return withContext(ioDispatcher) {
            try {
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * Only the part of the page covered by [tile]. The page is scaled to the
     * tile's render width, then shifted so the tile's top-left corner lands
     * at the bitmap origin; the engine clips everything else.
     *
     * @param reuse a pooled bitmap to render into instead of allocating; it is
     *   reconfigured to the tile size, so it must be large enough.
     */
    suspend fun renderTile(
        document: PdfDocument,
        tile: Tile,
        reuse: Bitmap? = null,
        transform: PageViewTransform? = null,
    ): Bitmap {
        val key = tile.key
        val pageSize = transform?.size ?: document.pageSizes[key.pageIndex]
        val scale = pageSize.scaleForWidth(key.renderWidth)
        val bitmap = whiteBitmap(tile.bounds.width, tile.bounds.height, reuse)
        val matrix = viewMatrix(transform, scale).apply {
            postTranslate(-tile.bounds.left.toFloat(), -tile.bounds.top.toFloat())
        }
        document.render(key.pageIndex, bitmap, matrix)
        return bitmap
    }

    /** Page points to bitmap pixels: through [transform], then scaled. */
    private fun viewMatrix(transform: PageViewTransform?, scale: Float): Matrix = Matrix().apply {
        transform?.affine?.let { m -> setValues(floatArrayOf(m.a, m.c, m.tx, m.b, m.d, m.ty, 0f, 0f, 1f)) }
        postScale(scale, scale)
    }

    // PDF pages have a transparent background; paint it white first.
    private suspend fun whiteBitmap(width: Int, height: Int, reuse: Bitmap? = null): Bitmap =
        withContext(ioDispatcher) {
            val bitmap = reuse?.apply { reconfigure(width, height, Bitmap.Config.ARGB_8888) }
                ?: createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.apply { eraseColor(Color.WHITE) }
        }
}
