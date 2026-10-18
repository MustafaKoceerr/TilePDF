package com.mustafakoceerr.tilepdf.core.text

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect

internal object LineMerger {

    /**
     * Joins consecutive character boxes into one rectangle per line. A box
     * starts a new line when it doesn't vertically overlap the current one,
     * or when it jumps back to the left (the next line of a column).
     */
    fun merge(boxes: List<PdfRect>): List<PdfRect> {
        if (boxes.isEmpty()) return emptyList()
        val lines = mutableListOf<PdfRect>()
        var current = boxes.first()
        for (box in boxes.drop(1)) {
            val overlap = minOf(current.bottom, box.bottom) - maxOf(current.top, box.top)
            val sameLine = overlap > 0.5f * minOf(current.height, box.height) &&
                box.left >= current.left - current.height
            if (sameLine) {
                current = current.union(box)
            } else {
                lines += current
                current = box
            }
        }
        lines += current
        return lines
    }
}
