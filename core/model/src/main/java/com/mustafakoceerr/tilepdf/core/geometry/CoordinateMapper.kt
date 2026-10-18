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
 * rotation-adjusted points. A page shown turned or cropped also passes
 * through its [PageViewTransform]; the scale comes from the laid-out size.
 */
object CoordinateMapper {

    /** The inverse, for a point: where a touch on the laid-out page falls in PDF points. */
    fun toPdfPoints(xPx: Float, yPx: Float, pageSize: PageSize, pageWidthPx: Float): Pair<Float, Float> {
        val scale = pageWidthPx / pageSize.widthPt
        return xPx / scale to yPx / scale
    }

    fun toPagePixels(rect: PdfRect, pageSize: PageSize, pageWidthPx: Float): PixelRectF {
        val scale = pageWidthPx / pageSize.widthPt
        return PixelRectF(rect.left * scale, rect.top * scale, rect.right * scale, rect.bottom * scale)
    }

    /** A touch on a page laid out through [transform], in the page's own points. */
    fun toPdfPoints(xPx: Float, yPx: Float, transform: PageViewTransform, pageWidthPx: Float): PdfPoint {
        val (x, y) = toPdfPoints(xPx, yPx, transform.size, pageWidthPx)
        return transform.toPage(PdfPoint(x, y))
    }

    /** A rectangle in the page's own points, where it shows on a page laid out through [transform]. */
    fun toPagePixels(rect: PdfRect, transform: PageViewTransform, pageWidthPx: Float): PixelRectF =
        toPagePixels(transform.toView(rect), transform.size, pageWidthPx)
}
