package com.mustafakoceerr.tilepdf.core.text

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch

/**
 * Finds a query in a page's extracted text, independent of which provider
 * extracted it.
 *
 * Matching is deliberately forgiving:
 * - case-insensitive, with Turkish dotted/dotless i treated as one letter, so
 *   "istanbul" finds "İSTANBUL" and "ılık" finds "ILIK";
 * - any run of whitespace, including line breaks, matches a single space, so
 *   a phrase wrapped onto the next line is still found.
 */
object TextSearcher {

    fun search(page: PageText, query: String): List<SearchMatch> {
        val needle = normalize(query).trim()
        if (needle.isEmpty()) return emptyList()

        // Normalized page text, plus the index in page.chars of every char in it.
        val haystack = StringBuilder(page.chars.size)
        val charIndex = IntArray(page.chars.size)
        var lastWasSpace = true
        page.chars.forEachIndexed { index, textChar ->
            val isSpace = textChar.char.isWhitespace()
            if (isSpace && lastWasSpace) return@forEachIndexed
            charIndex[haystack.length] = index
            haystack.append(if (isSpace) ' ' else fold(textChar.char))
            lastWasSpace = isSpace
        }

        val matches = mutableListOf<SearchMatch>()
        var start = haystack.indexOf(needle)
        while (start >= 0) {
            val boxes = (start until start + needle.length)
                .mapNotNull { page.chars[charIndex[it]].box }
            if (boxes.isNotEmpty()) matches += SearchMatch(page.pageIndex, mergeIntoLines(boxes))
            start = haystack.indexOf(needle, start + needle.length)
        }
        return matches
    }

    private fun normalize(text: String): String = buildString {
        var lastWasSpace = false
        for (char in text) {
            val isSpace = char.isWhitespace()
            if (isSpace && lastWasSpace) continue
            append(if (isSpace) ' ' else fold(char))
            lastWasSpace = isSpace
        }
    }

    /** Case folding that keeps one char per char, so indices stay aligned. */
    private fun fold(char: Char): Char = when (char) {
        'İ', 'I', 'ı' -> 'i'
        else -> char.lowercaseChar()
    }

    /**
     * Joins consecutive character boxes into one rectangle per line. A box
     * starts a new line when it doesn't vertically overlap the current one,
     * or when it jumps back to the left (the next line of a column).
     */
    private fun mergeIntoLines(boxes: List<PdfRect>): List<PdfRect> {
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
