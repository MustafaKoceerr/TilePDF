package com.mustafakoceerr.tilepdf.feature.reader.components

/**
 * A request for the page list to scroll to [pageIndex], optionally to a
 * position on the page in top-left PDF points (e.g. a chapter heading), or
 * to [offsetFraction] of the page as laid out (going back to where the
 * reader was, however the page is turned).
 */
data class PageJump(val pageIndex: Int, val topPt: Float? = null, val offsetFraction: Float? = null)
