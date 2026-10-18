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
