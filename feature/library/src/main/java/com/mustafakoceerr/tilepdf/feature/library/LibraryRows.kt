package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.LibrarySort
import java.text.Normalizer
import java.util.Locale

enum class LibraryFilter { All, Favorites }

private val COMBINING_MARKS = Regex("""\p{Mn}+""")

/**
 * [text] without case or accents, so "Sözleşme" and "sozlesme" compare
 * equal: accents are split off and dropped, and both Turkish i's become i.
 */
internal fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .replace('ı', 'i')
        .lowercase(Locale.ROOT)

/** Whether a document called [name] matches what the user typed; blank matches all. */
fun matchesQuery(name: String, query: String): Boolean = fold(name).contains(fold(query.trim()))

/** The library's rows: [hidden] (pending removal) left out, then filtered, searched and sorted. */
fun libraryRows(
    documents: List<LibraryDocument>,
    accessible: Set<String>,
    query: String,
    filter: LibraryFilter,
    sort: LibrarySort,
    hidden: Set<String>,
): List<LibraryItem> {
    val newestFirst = compareByDescending<LibraryDocument> { it.lastOpenedAt }
    val order = when (sort) {
        LibrarySort.Recent -> newestFirst
        LibrarySort.Name -> compareBy<LibraryDocument> { fold(it.displayName) }.then(newestFirst)
        LibrarySort.Added -> compareByDescending<LibraryDocument> { it.addedAt }.then(newestFirst)
    }
    return documents
        .filter { it.uri !in hidden }
        .filter { filter == LibraryFilter.All || it.favorite }
        .filter { matchesQuery(it.displayName, query) }
        .sortedWith(order)
        .map { LibraryItem(it, it.uri in accessible) }
}

/**
 * Whether the list should scroll back to its top after the first row became
 * [newTop]: a lazy list keeps its old first row in place, so the document
 * just read, now on top, would sit hidden above the screen. Only when the
 * list was at the top; a list scrolled away stays where it is.
 */
fun revealsNewTopRow(previousTop: String?, newTop: String?, firstVisibleIndex: Int): Boolean =
    previousTop != null && newTop != previousTop && firstVisibleIndex <= 1

/**
 * How far through its pages a document was read, `0..1`, counting the page
 * it was left on; `null` before reading past the first page or when the
 * length isn't known, so a row only shows progress that means something.
 */
fun readingProgress(pageIndex: Int, pageCount: Int): Float? =
    if (pageIndex <= 0 || pageCount <= 0) null else ((pageIndex + 1).toFloat() / pageCount).coerceAtMost(1f)
