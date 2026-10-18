package com.mustafakoceerr.tilepdf.core.geometry

/** The visible part of one page, in that page's own pixel space. */
data class VisiblePage(
    val pageIndex: Int,
    /** Laid out page size in pixels at the current zoom. */
    val pageWidth: Int,
    val pageHeight: Int,
    /** Region of the page on screen; origin is the page's top-left corner. */
    val visibleRegion: PixelRect,
)

/** The on-screen area of the page list, with its origin at the top-left. */
data class Viewport(val width: Int, val height: Int) {

    private val bounds = PixelRect(0, 0, width, height)

    /**
     * Converts a page's viewport-space [pageBounds] to the part of the page
     * that is on screen, or `null` if none of it is.
     */
    fun visiblePage(pageIndex: Int, pageBounds: PixelRect): VisiblePage? {
        val onScreen = pageBounds.intersect(bounds) ?: return null
        return VisiblePage(
            pageIndex = pageIndex,
            pageWidth = pageBounds.width,
            pageHeight = pageBounds.height,
            visibleRegion = onScreen.translate(-pageBounds.left, -pageBounds.top),
        )
    }
}
