package com.mustafakoceerr.tilepdf.core.pdf

import kotlin.math.roundToInt

/** Page dimensions in PDF points (1/72 inch), as reported by PdfRenderer. */
data class PageSize(val widthPt: Int, val heightPt: Int) {
    val aspectRatio: Float get() = widthPt.toFloat() / heightPt

    /** Points-to-pixels factor when the page is rendered [widthPx] wide. */
    fun scaleForWidth(widthPx: Int): Float = widthPx.toFloat() / widthPt

    /**
     * Pixel height when rendered [widthPx] wide. Every renderer derives page
     * height through here so whole-page drafts and tiles agree to the pixel.
     */
    fun heightForWidth(widthPx: Int): Int =
        (heightPt * scaleForWidth(widthPx)).roundToInt().coerceAtLeast(1)
}
