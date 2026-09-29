package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.model.OutlineEntry

/** Finds which table-of-contents entry the reader is in. */
object OutlineLocator {

    /**
     * The entry whose target is the last one at or before ([pageIndex],
     * [yPt]), the top of the screen in top-left PDF points; `null` before
     * the first entry.
     *
     * Compares positions rather than relying on list order, since some PDFs
     * list bookmarks out of page order. On a tie the later entry wins, so a
     * chapter and its first section on the same spot resolve to the section.
     */
    fun currentIndex(outline: List<OutlineEntry>, pageIndex: Int, yPt: Float): Int? {
        var best: Int? = null
        outline.forEachIndexed { index, entry ->
            val top = entry.topPt ?: 0f
            val reached = entry.pageIndex < pageIndex || (entry.pageIndex == pageIndex && top <= yPt)
            if (!reached) return@forEachIndexed
            val current = best?.let { outline[it] }
            if (current == null || !current.isAfter(entry)) best = index
        }
        return best
    }

    private fun OutlineEntry.isAfter(other: OutlineEntry): Boolean =
        pageIndex > other.pageIndex || (pageIndex == other.pageIndex && (topPt ?: 0f) > (other.topPt ?: 0f))
}
