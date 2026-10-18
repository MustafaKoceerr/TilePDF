package com.mustafakoceerr.tilepdf.core.render

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize

/** A channel darker than this is ink; lighter is paper, including scanner noise. */
private const val PAPER_LEVEL = 235

/** Cropping away less than this share of the page isn't worth the jump in layout. */
private const val MIN_CROPPED_SHARE = 0.05f

/**
 * The box around every pixel darker than paper in an ARGB image [width]
 * pixels wide, or null when it's blank.
 */
internal fun inkBounds(pixels: IntArray, width: Int, height: Int): PixelRect? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) {
        val row = y * width
        for (x in 0 until width) {
            if (!isInk(pixels[row + x])) continue
            if (x < left) left = x
            if (x > right) right = x
            if (y < top) top = y
            bottom = y
        }
    }
    return if (right < 0) null else PixelRect(left, top, right + 1, bottom + 1)
}

private fun isInk(argb: Int): Boolean {
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    return minOf(r, g, b) < PAPER_LEVEL
}

/**
 * The part of [page] worth showing when its margins are cropped: [ink] found
 * in a sample [sampleWidth] pixels wide, scaled to points and padded by
 * [paddingPt]. Null when there's nothing to crop: a blank page, or one that
 * is nearly all content.
 */
internal fun marginCrop(ink: PixelRect?, sampleWidth: Int, page: PageSize, paddingPt: Float): PdfRect? {
    ink ?: return null
    val ptPerPx = page.widthPt.toFloat() / sampleWidth
    val crop = PdfRect(
        left = (ink.left * ptPerPx - paddingPt).coerceAtLeast(0f),
        top = (ink.top * ptPerPx - paddingPt).coerceAtLeast(0f),
        right = (ink.right * ptPerPx + paddingPt).coerceAtMost(page.widthPt.toFloat()),
        bottom = (ink.bottom * ptPerPx + paddingPt).coerceAtMost(page.heightPt.toFloat()),
    )
    val kept = crop.width * crop.height / (page.widthPt.toFloat() * page.heightPt)
    return crop.takeIf { kept <= 1f - MIN_CROPPED_SHARE }
}
