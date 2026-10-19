package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.text.TextSelection
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.MarkupKind.Highlight
import com.mustafakoceerr.tilepdf.model.MarkupKind.StrikeOut
import com.mustafakoceerr.tilepdf.model.MarkupKind.Underline
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.TextMarkup
import org.junit.Assert.assertEquals
import org.junit.Test

private const val YELLOW = 0xFFFFEB3B.toInt()
private const val RED = 0xFFE53935.toInt()

// "alpha beta gamma delta" on one line, every char 10 pt wide, spaces too.
private val ALPHA = 0..4
private val BETA = 6..9
private val GAMMA = 11..15
private val DELTA = 17..21

class MarkupToggleTest {

    private val page: PageText = PageText.Builder().apply {
        "alpha beta gamma delta".forEachIndexed { i, char -> append(char, PdfRect(i * 10f, 100f, i * 10f + 10f, 112f)) }
    }.build(pageIndex = 0)

    private var nextId = 0

    private fun markup(id: String, kind: MarkupKind, range: IntRange, color: Int = YELLOW, pageIndex: Int = 0) =
        TextMarkup(id, pageIndex, color, kind, TextSelection.rects(page, range), TextSelection.text(page, range))

    private fun toggle(range: IntRange, kind: MarkupKind, own: List<TextMarkup>, color: Int = YELLOW) =
        toggleMarkup(page, range, kind, color, own) { "new${nextId++}" }

    /** What a markup shows, without its generated id. */
    private fun TextMarkup.looks() = listOf(pageIndex, color, kind, rects, text)

    private fun expected(kind: MarkupKind, range: IntRange, color: Int = YELLOW) = markup("", kind, range, color).looks()

    @Test
    fun unmarkedTextGetsTheMarkup() {
        val edit = toggle(BETA.first..GAMMA.last, Underline, own = emptyList())

        assertEquals(emptySet<String>(), edit.removed)
        assertEquals(listOf(expected(Underline, BETA.first..GAMMA.last)), edit.added.map { it.looks() })
    }

    @Test
    fun textMarkedThroughoutLosesTheMarkup() {
        val underline = markup("u", Underline, ALPHA.first..GAMMA.last)

        val edit = toggle(ALPHA.first..GAMMA.last, Underline, own = listOf(underline))

        assertEquals(setOf("u"), edit.removed)
        assertEquals(emptyList<Any>(), edit.added)
    }

    @Test
    fun turningOffTheMiddleWordKeepsTheWordsAroundIt() {
        val underline = markup("u", Underline, ALPHA.first..GAMMA.last, color = RED)

        val edit = toggle(BETA, Underline, own = listOf(underline))

        // Each keeps the old colour; the spaces next to the gap go with it.
        assertEquals(setOf("u"), edit.removed)
        assertEquals(
            listOf(expected(Underline, ALPHA, RED), expected(Underline, GAMMA, RED)),
            edit.added.map { it.looks() },
        )
    }

    @Test
    fun aPartlyMarkedSelectionIsMarkedThroughoutAsOne() {
        // Two of three words struck out; striking all three joins them up.
        val struck = markup("s", StrikeOut, ALPHA.first..BETA.last)

        val edit = toggle(ALPHA.first..GAMMA.last, StrikeOut, own = listOf(struck))

        assertEquals(setOf("s"), edit.removed)
        assertEquals(listOf(expected(StrikeOut, ALPHA.first..GAMMA.last)), edit.added.map { it.looks() })
    }

    @Test
    fun aSameColouredMarkupReachingFurtherIsJoinedWhole() {
        val struck = markup("s", StrikeOut, BETA.first..DELTA.last)

        val edit = toggle(ALPHA.first..GAMMA.last, StrikeOut, own = listOf(struck))

        assertEquals(setOf("s"), edit.removed)
        assertEquals(listOf(expected(StrikeOut, ALPHA.first..DELTA.last)), edit.added.map { it.looks() })
    }

    @Test
    fun aMarkupRightNextToTheSelectionIsJoinedWhenItsColourMatches() {
        val struck = markup("s", StrikeOut, ALPHA)

        val edit = toggle(BETA, StrikeOut, own = listOf(struck))

        assertEquals(setOf("s"), edit.removed)
        assertEquals(listOf(expected(StrikeOut, ALPHA.first..BETA.last)), edit.added.map { it.looks() })
    }

    @Test
    fun anotherColourKeepsItsPartOutsideTheSelection() {
        val struck = markup("s", StrikeOut, BETA.first..DELTA.last, color = RED)

        val edit = toggle(ALPHA.first..BETA.last, StrikeOut, own = listOf(struck))

        assertEquals(setOf("s"), edit.removed)
        assertEquals(
            listOf(expected(StrikeOut, GAMMA.first..DELTA.last, RED), expected(StrikeOut, ALPHA.first..BETA.last)),
            edit.added.map { it.looks() },
        )
    }

    @Test
    fun otherKindsAndPagesAreLeftAlone() {
        val underline = markup("u", Underline, ALPHA.first..DELTA.last)
        val strikeOnAnotherPage = markup("p", StrikeOut, ALPHA.first..DELTA.last, pageIndex = 1)

        val edit = toggle(BETA, StrikeOut, own = listOf(underline, strikeOnAnotherPage))

        assertEquals(emptySet<String>(), edit.removed)
        assertEquals(listOf(expected(StrikeOut, BETA)), edit.added.map { it.looks() })
    }

    @Test
    fun aSelectionEndingInsideAWordLeavesNoStubOfIt() {
        // A handle stopped one letter short of "gamma"'s end: no struck "a" may stay behind.
        val struck = markup("s", StrikeOut, ALPHA.first..GAMMA.last)

        val edit = toggle(ALPHA.first..GAMMA.last - 1, StrikeOut, own = listOf(struck))

        assertEquals(setOf("s"), edit.removed)
        assertEquals(emptyList<Any>(), edit.added)
    }

    @Test
    fun partOfAWordMarksTheWholeWord() {
        val edit = toggle(BETA.first + 1..GAMMA.first + 1, Underline, own = emptyList())

        assertEquals(listOf(expected(Underline, BETA.first..GAMMA.last)), edit.added.map { it.looks() })
    }

    @Test
    fun spacesDontCountTowardsWhatIsMarked() {
        // "beta " selected; only "beta" is highlighted, which is all of its text.
        val highlight = markup("h", Highlight, BETA)

        val edit = toggle(BETA.first..BETA.last + 1, Highlight, own = listOf(highlight))

        assertEquals(setOf("h"), edit.removed)
        assertEquals(emptyList<Any>(), edit.added)
    }
}
