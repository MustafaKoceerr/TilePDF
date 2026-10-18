package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import kotlin.math.hypot

/**
 * Collects a pen stroke in page points, skipping points closer than
 * [minDistance] to the last kept one: touch events arrive far more often
 * than the line needs, and every point is stored in the PDF.
 */
class StrokeBuilder(private val minDistance: Float = 0.75f) {

    private val kept = mutableListOf<PdfPoint>()

    val points: List<PdfPoint> get() = kept

    /** Returns whether [point] was kept. */
    fun add(point: PdfPoint): Boolean {
        val last = kept.lastOrNull()
        if (last != null && hypot(point.x - last.x, point.y - last.y) < minDistance) return false
        kept += point
        return true
    }
}
