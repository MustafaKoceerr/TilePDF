package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.text.TextSelection
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.TextMarkup
import com.mustafakoceerr.tilepdf.model.newAnnotationId

/** How far a character's centre may sit outside a markup's line and still count as marked. */
private const val COVER_TOLERANCE_PT = 0.5f

/** What toggling a markup changes, as one step: markups to remove and to add. */
data class MarkupEdit(val removed: Set<String>, val added: List<TextMarkup>)

/**
 * Toggles [kind] over [range] on [page], as a bold button does in an editor.
 * Only TilePDF's own markups of [kind] on that page take part.
 *
 * - All of the selected text already has it: it comes off the selection. A
 *   markup reaching past the selection keeps its parts before and after,
 *   in its own colour.
 * - Some doesn't: the whole selection gets it in [color], as one markup.
 *   Markups of that colour overlapping or right next to the selection join
 *   it; others keep only their parts outside it.
 *
 * Spaces don't count, so selecting a word with the space after it still
 * finds the word's markup whole.
 */
fun toggleMarkup(
    page: PageText,
    range: IntRange,
    kind: MarkupKind,
    color: Int,
    own: List<Annotation>,
    newId: () -> String = ::newAnnotationId,
): MarkupEdit {
    val selected = range.filter(page::isMarkable)
    if (selected.isEmpty()) return MarkupEdit(emptySet(), emptyList())
    val marks = own.asSequence()
        .filterIsInstance<TextMarkup>()
        .filter { it.pageIndex == page.pageIndex && it.kind == kind }
        .mapNotNull { markup -> page.markedRange(markup)?.let { markup to it } }
        .toList()
    fun create(run: IntRange, color: Int) =
        TextMarkup(newId(), page.pageIndex, color, kind, TextSelection.rects(page, run), TextSelection.text(page, run))
    fun outsideParts(marked: IntRange, color: Int) = listOfNotNull(
        page.trim(marked.first until range.first)?.let { create(it, color) },
        page.trim(range.last + 1..marked.last)?.let { create(it, color) },
    )

    val removed = mutableSetOf<String>()
    val added = mutableListOf<TextMarkup>()
    if (selected.all { index -> marks.any { (_, marked) -> index in marked } }) {
        for ((markup, marked) in marks) {
            if (!marked.overlaps(range)) continue
            removed += markup.id
            added += outsideParts(marked, markup.color)
        }
        return MarkupEdit(removed, added)
    }

    var start = selected.first()
    var end = selected.last()
    for ((markup, marked) in marks) {
        if (!marked.overlaps(range) && !page.touches(marked, range)) continue
        removed += markup.id
        if (markup.color == color) {
            start = minOf(start, marked.first)
            end = maxOf(end, marked.last)
        } else {
            added += outsideParts(marked, markup.color)
        }
    }
    added += create(start..end, color)
    return MarkupEdit(removed, added)
}

/** A character a markup can cover: it has a box and isn't a space. */
private fun PageText.isMarkable(index: Int): Boolean = box(index) != null && !text[index].isWhitespace()

/** [run] without the spaces at its ends, or null when nothing is left. */
private fun PageText.trim(run: IntRange): IntRange? {
    val first = run.firstOrNull(::isMarkable) ?: return null
    return first..run.last(::isMarkable)
}

/** The first to last character whose centre lies on one of [markup]'s lines. */
private fun PageText.markedRange(markup: TextMarkup): IntRange? {
    var first = -1
    var last = -1
    for (index in text.indices) {
        if (!isMarkable(index)) continue
        val box = box(index) ?: continue
        val x = (box.left + box.right) / 2
        val y = (box.top + box.bottom) / 2
        val covered = markup.rects.any {
            x >= it.left - COVER_TOLERANCE_PT && x <= it.right + COVER_TOLERANCE_PT &&
                y >= it.top - COVER_TOLERANCE_PT && y <= it.bottom + COVER_TOLERANCE_PT
        }
        if (covered) {
            if (first < 0) first = index
            last = index
        }
    }
    return if (first < 0) null else first..last
}

private fun IntRange.overlaps(other: IntRange): Boolean = first <= other.last && other.first <= last

/** Whether [marked] and [range] are separated by nothing but spaces. */
private fun PageText.touches(marked: IntRange, range: IntRange): Boolean = when {
    marked.last < range.first -> (marked.last + 1 until range.first).none(::isMarkable)
    range.last < marked.first -> (range.last + 1 until marked.first).none(::isMarkable)
    else -> false
}
