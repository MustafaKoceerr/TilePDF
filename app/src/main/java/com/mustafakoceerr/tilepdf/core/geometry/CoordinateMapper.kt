package com.mustafakoceerr.tilepdf.core.geometry

import com.mustafakoceerr.tilepdf.core.pdf.PageSize

/** Rectangle in pixels, with fractional edges for precise drawing. */
data class PixelRectF(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
}

/**
 * Converts PDF point-space rectangles to the pixel space of a page as it is
 * laid out. Uses the same points-to-pixels factor as the renderer
 * ([PageSize.scaleForWidth]), so highlights line up with rendered text at any
 * zoom.
 *
 * Text providers already deliver top-left-origin, crop-box-relative,
 * rotation-adjusted points, so this is a pure scale.
 */
object CoordinateMapper {

    fun toPagePixels(rect: PdfRect, pageSize: PageSize, pageWidthPx: Float): PixelRectF {
        val scale = pageWidthPx / pageSize.widthPt
        return PixelRectF(rect.left * scale, rect.top * scale, rect.right * scale, rect.bottom * scale)
    }
}
