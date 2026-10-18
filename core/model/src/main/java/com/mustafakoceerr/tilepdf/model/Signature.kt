package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint

/**
 * A drawn signature, in units of its own width: x runs 0..1 and y 0..[height].
 * Placing it on a page only scales and moves it.
 */
data class Signature(val strokes: List<List<PdfPoint>>, val height: Float)

/** [strokes] as drawn, in any units, fitted into a [Signature]; null when nothing was drawn. */
fun normalizeSignature(strokes: List<List<PdfPoint>>): Signature? {
    val points = strokes.flatten()
    if (points.isEmpty()) return null
    val left = points.minOf { it.x }
    val top = points.minOf { it.y }
    val width = points.maxOf { it.x } - left
    val height = points.maxOf { it.y } - top
    // A vertical line has no width: its height sets the unit instead.
    val unit = width.takeIf { it > 0f } ?: height.takeIf { it > 0f } ?: 1f
    return Signature(
        strokes = strokes.filter { it.isNotEmpty() }.map { stroke -> stroke.map { PdfPoint((it.x - left) / unit, (it.y - top) / unit) } },
        height = height / unit,
    )
}

/** The strokes in page points, [widthPt] wide and centred on [centre]. */
fun Signature.placedAround(centre: PdfPoint, widthPt: Float): List<List<PdfPoint>> {
    val left = centre.x - widthPt / 2
    val top = centre.y - height * widthPt / 2
    return strokes.map { stroke -> stroke.map { PdfPoint(left + it.x * widthPt, top + it.y * widthPt) } }
}
