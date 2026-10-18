package com.mustafakoceerr.tilepdf.core.geometry

import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import kotlin.math.ceil
import kotlin.math.floor

/** `x' = a·x + c·y + tx`, `y' = b·x + d·y + ty`: the layout of android.graphics.Matrix and Compose's. */
data class Affine(val a: Float, val b: Float, val c: Float, val d: Float, val tx: Float, val ty: Float)

/**
 * How a page is shown: cropped to [crop], then turned clockwise by [rotation]
 * degrees. Only the view changes; the document, its text and annotations stay
 * in page points, and everything drawn or touched passes through here.
 */
data class PageViewTransform private constructor(
    /** The page as the document has it. */
    val page: PageSize,
    /** 0, 90, 180 or 270. */
    val rotation: Int,
    /** The part of the page shown, in whole points; the full page when not cropped. */
    val crop: PdfRect,
) {

    private val cropWidth = crop.width
    private val cropHeight = crop.height

    /** The page as laid out: cropped, and sideways for a quarter turn. */
    val size: PageSize = if (rotation % 180 == 0) {
        PageSize(cropWidth.toInt(), cropHeight.toInt())
    } else {
        PageSize(cropHeight.toInt(), cropWidth.toInt())
    }

    val isIdentity: Boolean get() = rotation == 0 && crop == fullPage(page)

    /** Page points to view points. */
    val affine: Affine = when (rotation) {
        90 -> Affine(a = 0f, b = 1f, c = -1f, d = 0f, tx = cropHeight + crop.top, ty = -crop.left)
        180 -> Affine(a = -1f, b = 0f, c = 0f, d = -1f, tx = cropWidth + crop.left, ty = cropHeight + crop.top)
        270 -> Affine(a = 0f, b = -1f, c = 1f, d = 0f, tx = -crop.top, ty = cropWidth + crop.left)
        else -> Affine(a = 1f, b = 0f, c = 0f, d = 1f, tx = -crop.left, ty = -crop.top)
    }

    fun toView(point: PdfPoint): PdfPoint = with(affine) {
        PdfPoint(a * point.x + c * point.y + tx, b * point.x + d * point.y + ty)
    }

    fun toPage(point: PdfPoint): PdfPoint {
        val (vx, vy) = point
        val (qx, qy) = when (rotation) {
            90 -> vy to cropHeight - vx
            180 -> cropWidth - vx to cropHeight - vy
            270 -> cropWidth - vy to vx
            else -> vx to vy
        }
        return PdfPoint(qx + crop.left, qy + crop.top)
    }

    /** Quarter turns keep rectangles axis-aligned; the corners are sorted again. */
    fun toView(rect: PdfRect): PdfRect {
        val a = toView(PdfPoint(rect.left, rect.top))
        val b = toView(PdfPoint(rect.right, rect.bottom))
        return PdfRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
    }

    companion object {

        /** [rotation] in any multiple of 90 degrees; [crop] null for the whole page. */
        fun of(page: PageSize, rotation: Int = 0, crop: PdfRect? = null): PageViewTransform {
            require(rotation % 90 == 0) { "Pages turn in quarter turns, not $rotation°" }
            val shown = crop?.let { snap(it, page) } ?: fullPage(page)
            return PageViewTransform(page, Math.floorMod(rotation, 360), shown)
        }

        private fun fullPage(page: PageSize) = PdfRect(0f, 0f, page.widthPt.toFloat(), page.heightPt.toFloat())

        /** Whole points, so the view size is exact; never off the page. */
        private fun snap(crop: PdfRect, page: PageSize) = PdfRect(
            left = floor(crop.left).coerceIn(0f, page.widthPt.toFloat()),
            top = floor(crop.top).coerceIn(0f, page.heightPt.toFloat()),
            right = ceil(crop.right).coerceIn(0f, page.widthPt.toFloat()),
            bottom = ceil(crop.bottom).coerceIn(0f, page.heightPt.toFloat()),
        )
    }
}
