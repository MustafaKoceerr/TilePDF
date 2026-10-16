package com.mustafakoceerr.tilepdf.core.geometry

/**
 * Integer pixel rectangle, right/bottom exclusive.
 *
 * Kept free of `android.graphics` so geometry code runs in plain JVM tests.
 */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {

    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    /** The overlap with [other], or `null` if they don't overlap. */
    fun intersect(other: PixelRect): PixelRect? {
        val result = PixelRect(
            left = maxOf(left, other.left),
            top = maxOf(top, other.top),
            right = minOf(right, other.right),
            bottom = minOf(bottom, other.bottom),
        )
        return result.takeUnless { it.isEmpty }
    }

    fun translate(dx: Int, dy: Int) = PixelRect(left + dx, top + dy, right + dx, bottom + dy)

    companion object {
        fun ofSize(left: Int, top: Int, width: Int, height: Int) =
            PixelRect(left, top, left + width, top + height)
    }
}
