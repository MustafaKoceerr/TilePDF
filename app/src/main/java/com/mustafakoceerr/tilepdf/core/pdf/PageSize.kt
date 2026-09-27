package com.mustafakoceerr.tilepdf.core.pdf

/** Page dimensions in PDF points (1/72 inch), as reported by PdfRenderer. */
data class PageSize(val widthPt: Int, val heightPt: Int) {
    val aspectRatio: Float get() = widthPt.toFloat() / heightPt
}
