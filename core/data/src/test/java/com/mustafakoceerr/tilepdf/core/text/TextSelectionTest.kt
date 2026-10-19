package com.mustafakoceerr.tilepdf.core.text

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextSelectionTest {

    /** 10 pt wide, 12 pt tall chars, 20 pt per line; box-less separators like PDFium generates. */
    private fun page(vararg lines: String): PageText {
        val builder = PageText.Builder()
        lines.forEachIndexed { row, line ->
            if (row > 0) builder.append('\n', null)
            line.forEachIndexed { col, char ->
                val top = row * 20f
                builder.append(char, if (char == ' ') null else PdfRect(col * 10f, top, col * 10f + 10f, top + 12f))
            }
        }
        return builder.build(0)
    }

    private val twoLines = page("tile based render", "memory cache.")

    @Test
    fun longPressSelectsTheWholeWord() {
        // x 72 is inside "based" (cols 5..9), y 6 is line 1.
        val range = TextSelection.wordAt(twoLines, x = 72f, y = 6f)!!
        assertEquals("based", TextSelection.text(twoLines, range))
        assertEquals(listOf(PdfRect(50f, 0f, 100f, 12f)), TextSelection.rects(twoLines, range))
    }

    @Test
    fun aRangeEndingInsideAWordGrowsToWholeWords() {
        // "ile based rend": starts inside "tile", ends inside "render".
        val range = TextSelection.wholeWords(twoLines, 1..13)
        assertEquals("tile based render", TextSelection.text(twoLines, range))
    }

    @Test
    fun wholeWordsStayAsTheyAre() {
        // "based" is cols 5..9; punctuation isn't part of a word.
        assertEquals(5..9, TextSelection.wholeWords(twoLines, 5..9))
        val cache = twoLines.text.indexOf("cache")
        assertEquals(cache..cache + 4, TextSelection.wholeWords(twoLines, cache..cache + 4))
    }

    @Test
    fun punctuationSelectsOnlyItself() {
        // "." is col 12 of line 2.
        val range = TextSelection.wordAt(twoLines, x = 125f, y = 26f)!!
        assertEquals(".", TextSelection.text(twoLines, range))
    }

    @Test
    fun pressFarFromTextSelectsNothing() {
        assertNull(TextSelection.wordAt(twoLines, x = 400f, y = 300f))
    }

    @Test
    fun nearestCharPrefersTheSameLine() {
        // Far right of line 1: its last char, not something on another line.
        // Between the lines but nearer line 2: line 2's first char.
        assertEquals(twoLines.text.indexOf("render") + 5, TextSelection.charAt(twoLines, x = 500f, y = 6f))
        assertEquals(twoLines.text.indexOf("memory"), TextSelection.charAt(twoLines, x = 0f, y = 17f))
    }

    @Test
    fun selectionAcrossLinesCopiesWithLineBreakAndOneRectPerLine() {
        val start = twoLines.text.indexOf("render")
        val end = twoLines.text.indexOf("memory") + 5
        val range = start..end

        assertEquals("render\nmemory", TextSelection.text(twoLines, range))
        assertEquals(2, TextSelection.rects(twoLines, range).size)
    }

    @Test
    fun selectAllCoversEveryCharWithABox() {
        assertEquals("tile based render\nmemory cache.", TextSelection.text(twoLines, TextSelection.all(twoLines)!!))
    }
}
