package com.mustafakoceerr.tilepdf.feature.reader.components

/**
 * A request for the page list to scroll to [pageIndex], optionally to a
 * position on the page in top-left PDF points (e.g. a chapter heading).
 */
data class PageJump(val pageIndex: Int, val topPt: Float? = null)
