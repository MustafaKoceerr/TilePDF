package com.mustafakoceerr.tilepdf.core.render

/**
 * Zoom levels tiles are rendered at, relative to the fit-to-width size.
 *
 * Rendering at fixed levels instead of the exact zoom lets small pinch
 * adjustments reuse cached tiles. The level is always at or above the zoom,
 * so tiles are only ever scaled down on screen and never look blurry.
 */
object RenderScale {

    val levels = floatArrayOf(1f, 1.5f, 2f, 3f, 4f, 5f)

    /** Tolerance so float noise like 2.0000002 doesn't jump a whole level. */
    private const val EPSILON = 0.01f

    fun levelFor(zoom: Float): Float =
        levels.firstOrNull { it >= zoom - EPSILON } ?: levels.last()
}
