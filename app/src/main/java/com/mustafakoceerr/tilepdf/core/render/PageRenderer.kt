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
import kotlin.math.roundToInt

/** Renders a whole page into a single bitmap scaled to a target width. */
class PageRenderer @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun render(document: PdfDocument, pageIndex: Int, targetWidthPx: Int): Bitmap {
        val pageSize = document.pageSizes[pageIndex]
        val scale = targetWidthPx.toFloat() / pageSize.widthPt
        val heightPx = (pageSize.heightPt * scale).roundToInt().coerceAtLeast(1)

        val bitmap = withContext(ioDispatcher) {
            // PDF pages have a transparent background; paint it white first.
            createBitmap(targetWidthPx, heightPx, Bitmap.Config.ARGB_8888)
                .apply { eraseColor(Color.WHITE) }
        }
        document.render(pageIndex, bitmap, Matrix().apply { setScale(scale, scale) })
        return bitmap
    }
}
