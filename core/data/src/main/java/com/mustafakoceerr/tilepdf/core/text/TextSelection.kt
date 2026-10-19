package com.mustafakoceerr.tilepdf.core.text

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.PageText

/**
 * A long press further than this from any text selects nothing, so pressing
 * blank margins doesn't grab a word from across the page.
 */
private const val MAX_WORD_DISTANCE_PT = 12f

/** Vertical distance counts this much more than horizontal: stay on the line. */
private const val LINE_BIAS = 4f

/**
 * Text selection on one page, in PDF points. Ranges index into
 * [PageText.text] and are inclusive, like [IntRange].
 */
object TextSelection {

    /**
     * The word under ([x], [y]), or `null` if the point isn't near any text.
     * A punctuation mark or symbol on its own selects just that character.
     */
    fun wordAt(page: PageText, x: Float, y: Float): IntRange? {
        val index = nearestChar(page, x, y, MAX_WORD_DISTANCE_PT) ?: return null
        if (!page.isWordChar(index)) return index..index
        var start = index
        while (start > 0 && page.isWordChar(start - 1)) start--
        var end = index
        while (end < page.text.lastIndex && page.isWordChar(end + 1)) end++
        return start..end
    }

    /**
     * [range] grown to whole words at both ends: a handle dragged one letter
     * short or past a word's edge still means the word.
     */
    fun wholeWords(page: PageText, range: IntRange): IntRange {
        var start = range.first
        while (start > 0 && page.isWordChar(start) && page.isWordChar(start - 1)) start--
        var end = range.last
        while (end < page.text.lastIndex && page.isWordChar(end) && page.isWordChar(end + 1)) end++
        return start..end
    }

    /** The character nearest ([x], [y]), preferring its own line; for dragging handles. */
    fun charAt(page: PageText, x: Float, y: Float): Int? = nearestChar(page, x, y, Float.MAX_VALUE)

    /** Every character that has a box: the whole page's text. */
    fun all(page: PageText): IntRange? {
        val first = page.text.indices.firstOrNull { page.box(it) != null } ?: return null
        val last = page.text.indices.last { page.box(it) != null }
        return first..last
    }

    /** What gets copied; the provider's separators give natural spaces and line breaks. */
    fun text(page: PageText, range: IntRange): String =
        page.text.substring(range.first, range.last + 1).trim()

    /** One rectangle per line, for drawing the selection. */
    fun rects(page: PageText, range: IntRange): List<PdfRect> =
        LineMerger.merge(range.mapNotNull { page.box(it) })

    private fun nearestChar(page: PageText, x: Float, y: Float, maxDistance: Float): Int? {
        var best: Int? = null
        var bestScore = Float.MAX_VALUE
        for (index in page.text.indices) {
            val box = page.box(index) ?: continue
            val dx = distanceOutside(x, box.left, box.right)
            val dy = distanceOutside(y, box.top, box.bottom)
            if (maxOf(dx, dy) > maxDistance) continue
            val score = dy * LINE_BIAS + dx
            if (score < bestScore) {
                bestScore = score
                best = index
            }
        }
        return best
    }

    private fun distanceOutside(value: Float, min: Float, max: Float): Float = when {
        value < min -> min - value
        value > max -> value - max
        else -> 0f
    }

    private fun PageText.isWordChar(index: Int): Boolean {
        if (box(index) == null) return false
        val char = text[index]
        return char.isLetterOrDigit() || char == '\'' || char == '’' || char == '-' || char == '_'
    }
}
