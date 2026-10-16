package com.mustafakoceerr.tilepdf.core.geometry

/**
 * Horizontal placement shared by every page at the current zoom, in viewport
 * pixels. Vertical placement differs per page and comes from the list layout.
 *
 * @param pageLeft x of every page's left edge; negative when panned right.
 * @param pageWidth laid out width of every page (pages fill the list width).
 */
data class PageLayout(val pageLeft: Int, val pageWidth: Int) {

    /** Bounds of a page whose top edge is at viewport y [top]. */
    fun pageBounds(top: Int, height: Int): PixelRect =
        PixelRect.ofSize(pageLeft, top, pageWidth, height)
}
