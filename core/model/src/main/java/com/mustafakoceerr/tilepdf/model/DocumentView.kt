package com.mustafakoceerr.tilepdf.model

/** How one document's pages are shown; kept with its library entry. */
data class DocumentView(
    /** Clockwise, in quarter turns: 0, 90, 180 or 270. */
    val rotation: Int = 0,
    /** Each page shown without its empty margins. */
    val cropMargins: Boolean = false,
)
