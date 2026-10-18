package com.mustafakoceerr.tilepdf.core.pdf

/** A link annotation in a PDF, as the engine reports it. */
sealed interface PdfLink {
    /** To [pageIndex], at [topPt] points from its top when the link says so. */
    data class GoTo(val pageIndex: Int, val topPt: Float?) : PdfLink

    /** To an external [uri], unfiltered: the reader decides what is safe to open. */
    data class Uri(val uri: String) : PdfLink
}
