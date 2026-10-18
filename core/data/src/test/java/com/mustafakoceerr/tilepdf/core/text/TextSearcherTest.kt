package com.mustafakoceerr.tilepdf.core.text

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.PageText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearcherTest {

    /**
     * Lays [lines] out as 10 pt wide, 12 pt tall chars, 20 pt per line, with
     * box-less separators between words and lines like PDFium generates.
     */
    private fun page(vararg lines: String): PageText {
        val builder = PageText.Builder(expectedLength = 4)
        lines.forEachIndexed { row, line ->
            if (row > 0) builder.append('\n', null)
            line.forEachIndexed { col, char ->
                val top = row * 20f
                val box = if (char == ' ') null else PdfRect(col * 10f, top, col * 10f + 10f, top + 12f)
                builder.append(char, box)
            }
        }
        return builder.build(pageIndex = 4)
    }

    @Test
    fun findsAllOccurrencesWithTheirBoxes() {
        val matches = TextSearcher.search(page("cat and cat"), "cat")

        assertEquals(2, matches.size)
        assertEquals(4, matches[0].pageIndex)
        assertEquals(listOf(PdfRect(0f, 0f, 30f, 12f)), matches[0].rects)
        assertEquals(listOf(PdfRect(80f, 0f, 110f, 12f)), matches[1].rects)
    }

    @Test
    fun ignoresCaseIncludingTurkishI() {
        val page = page("İSTANBUL ILIK ısı Istanbul")

        assertEquals(2, TextSearcher.search(page, "istanbul").size)
        assertEquals(1, TextSearcher.search(page, "ılık").size)
        assertEquals(1, TextSearcher.search(page, "ISI").size)
    }

    @Test
    fun phraseWrappedAcrossLinesGivesOneRectPerLine() {
        val matches = TextSearcher.search(page("see the tile", "engine here"), "tile  engine")

        assertEquals(1, matches.size)
        assertEquals(
            listOf(PdfRect(80f, 0f, 120f, 12f), PdfRect(0f, 20f, 60f, 32f)),
            matches[0].rects,
        )
    }

    @Test
    fun blankQueryMatchesNothing() {
        assertTrue(TextSearcher.search(page("anything"), "   ").isEmpty())
    }

    @Test
    fun matchesDoNotOverlap() {
        assertEquals(2, TextSearcher.search(page("aaaa"), "aa").size)
    }

    @Test
    fun builderGrowsAndKeepsBoxesAligned() {
        // Starts with room for 4 chars, so this forces several resizes.
        val page = page("abcdefghij klmnop")

        assertEquals("abcdefghij klmnop", page.text)
        assertEquals(PdfRect(90f, 0f, 100f, 12f), page.box(9))
        assertEquals(null, page.box(10)) // the space
        assertEquals(PdfRect(160f, 0f, 170f, 12f), page.box(16))
    }
}
