package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect

/**
 * A page's extracted text with one box per character, in PDF point space
 * (top-left origin, relative to the crop box, page rotation applied). Every
 * text provider produces this format, so coordinate mapping never depends on
 * where the text came from.
 *
 * Stored compactly because whole documents are kept in memory for search:
 * a String plus four floats per char is ~18 bytes per char, versus ~80 for
 * an object per char and per box. Word and line separators the provider
 * inserts have no box.
 */
class PageText(
    val pageIndex: Int,
    val text: String,
    /** left, top, right, bottom per char; NaN for chars without a box. */
    private val boxes: FloatArray,
) {
    init {
        require(boxes.size == text.length * FLOATS_PER_BOX) { "One box per char expected" }
    }

    fun box(index: Int): PdfRect? {
        val offset = index * FLOATS_PER_BOX
        if (boxes[offset].isNaN()) return null
        return PdfRect(boxes[offset], boxes[offset + 1], boxes[offset + 2], boxes[offset + 3])
    }

    class Builder(expectedLength: Int = 2048) {
        private val text = StringBuilder(expectedLength)
        private var boxes = FloatArray(expectedLength * FLOATS_PER_BOX)

        fun append(char: Char, box: PdfRect?) {
            val offset = text.length * FLOATS_PER_BOX
            if (offset + FLOATS_PER_BOX > boxes.size) boxes = boxes.copyOf(boxes.size * 2)
            text.append(char)
            boxes[offset] = box?.left ?: Float.NaN
            boxes[offset + 1] = box?.top ?: Float.NaN
            boxes[offset + 2] = box?.right ?: Float.NaN
            boxes[offset + 3] = box?.bottom ?: Float.NaN
        }

        fun build(pageIndex: Int) =
            PageText(pageIndex, text.toString(), boxes.copyOf(text.length * FLOATS_PER_BOX))
    }

    companion object {
        private const val FLOATS_PER_BOX = 4

        /** Text from a source that reports no per-character positions. */
        fun withoutBoxes(pageIndex: Int, text: String) =
            PageText(pageIndex, text, FloatArray(text.length * FLOATS_PER_BOX) { Float.NaN })
    }
}

/** One search hit; [rects] are per-line boxes in PDF point space. */
data class SearchMatch(val pageIndex: Int, val rects: List<PdfRect>)
