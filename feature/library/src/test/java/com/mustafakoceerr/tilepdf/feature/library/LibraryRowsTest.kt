package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.LibrarySort
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRowsTest {

    private fun doc(name: String, opened: Long, added: Long = opened, favorite: Boolean = false) =
        LibraryDocument("content://$name", name, 10, ReadingPosition(), opened, added, favorite)

    private val documents = listOf(
        doc("Defter.pdf", opened = 3, added = 1),
        doc("çay.pdf", opened = 1, added = 3, favorite = true),
        doc("Kalem.pdf", opened = 2, added = 2),
        doc("ılık.pdf", opened = 4, added = 0),
    )

    private fun rows(
        query: String = "",
        filter: LibraryFilter = LibraryFilter.All,
        sort: LibrarySort = LibrarySort.Recent,
        hidden: Set<String> = emptySet(),
        accessible: Set<String> = documents.map { it.uri }.toSet(),
    ) = libraryRows(documents, accessible, query, filter, sort, hidden)

    private fun List<LibraryItem>.names() = map { it.document.displayName }

    @Test
    fun recentIsNewestFirst() {
        assertEquals(listOf("ılık.pdf", "Defter.pdf", "Kalem.pdf", "çay.pdf"), rows().names())
    }

    @Test
    fun nameSortFoldsCase() {
        assertEquals(listOf("çay.pdf", "Defter.pdf", "ılık.pdf", "Kalem.pdf"), rows(sort = LibrarySort.Name).names())
    }

    @Test
    fun addedSortNewestFirst() {
        assertEquals(listOf("çay.pdf", "Kalem.pdf", "Defter.pdf", "ılık.pdf"), rows(sort = LibrarySort.Added).names())
    }

    @Test
    fun favoritesFilter() {
        assertEquals(listOf("çay.pdf"), rows(filter = LibraryFilter.Favorites).names())
    }

    @Test
    fun queryFoldsAccents() {
        assertTrue(matchesQuery("Sözleşme.pdf", "sozlesme"))
        assertTrue(matchesQuery("Sözleşme.pdf", "  SÖZ "))
        assertFalse(matchesQuery("Sözleşme.pdf", "fatura"))
        assertEquals(listOf("çay.pdf"), rows(query = "CAY").names())
    }

    @Test
    fun turkishIMatches() {
        assertTrue(matchesQuery("ışık.pdf", "ISIK"))
        assertTrue(matchesQuery("İstanbul.pdf", "istanbul"))
        assertTrue(matchesQuery("Istanbul.pdf", "ıstanbul"))
    }

    @Test
    fun blankQueryMatchesAll() {
        assertEquals(4, rows(query = "   ").size)
    }

    @Test
    fun hiddenAreLeftOut() {
        assertEquals(listOf("ılık.pdf", "Kalem.pdf", "çay.pdf"), rows(hidden = setOf("content://Defter.pdf")).names())
    }

    @Test
    fun accessibilityIsCarried() {
        val items = rows(accessible = setOf("content://Kalem.pdf"))
        assertEquals(listOf(false, false, true, false), items.map { it.isAccessible })
    }
}
