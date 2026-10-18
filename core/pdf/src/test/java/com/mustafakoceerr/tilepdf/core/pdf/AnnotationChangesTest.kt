package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationChangesTest {

    private fun note(id: String, page: Int): Annotation = Note(id, page, color = 0xFFFFEB3B.toInt(), at = PdfPoint(10f, 10f), text = "")

    @Test
    fun unchangedIdsAreLeftAlone() {
        val changes = annotationChanges(
            existing = mapOf(0 to setOf("tilepdf-a")),
            wanted = listOf(note("tilepdf-a", 0)),
            existingForeign = emptyMap(),
            removedForeign = emptySet(),
        )
        assertTrue(changes.isEmpty)
    }

    @Test
    fun missingAreAdded() {
        val added = note("tilepdf-b", 1)
        val changes = annotationChanges(mapOf(0 to setOf("tilepdf-a")), listOf(note("tilepdf-a", 0), added), emptyMap(), emptySet())
        assertEquals(listOf(added), changes.additions)
        assertEquals(emptyMap<Int, Set<String>>(), changes.removals)
    }

    @Test
    fun extraAreRemovedPerPage() {
        val changes = annotationChanges(
            existing = mapOf(0 to setOf("tilepdf-a", "tilepdf-b"), 2 to setOf("tilepdf-c")),
            wanted = listOf(note("tilepdf-a", 0)),
            existingForeign = emptyMap(),
            removedForeign = emptySet(),
        )
        assertEquals(mapOf(0 to setOf("tilepdf-b"), 2 to setOf("tilepdf-c")), changes.removals)
    }

    @Test
    fun nothingToDoIsEmpty() {
        assertTrue(annotationChanges(emptyMap(), emptyList(), emptyMap(), emptySet()).isEmpty)
    }

    @Test
    fun emptyPagesAreNotListed() {
        val changes = annotationChanges(
            existing = mapOf(0 to emptySet(), 1 to setOf("tilepdf-a")),
            wanted = listOf(note("tilepdf-a", 1)),
            existingForeign = mapOf(0 to setOf("acrobat-1")),
            removedForeign = emptySet(),
        )
        assertTrue(changes.removals.isEmpty())
        assertTrue(changes.foreignRemovals.isEmpty())
    }

    @Test
    fun removedForeignIsRemoved() {
        val changes = annotationChanges(
            existing = emptyMap(),
            wanted = emptyList(),
            existingForeign = mapOf(0 to setOf("acrobat-1", "#2")),
            removedForeign = setOf(ForeignRef(0, "#2")),
        )
        assertEquals(mapOf(0 to setOf("#2")), changes.foreignRemovals)
        assertFalse(changes.isEmpty)
    }

    @Test
    fun removedForeignAlreadyGoneIsIgnored() {
        val changes = annotationChanges(
            existing = emptyMap(),
            wanted = emptyList(),
            existingForeign = mapOf(0 to setOf("acrobat-1")),
            removedForeign = setOf(ForeignRef(0, "acrobat-2"), ForeignRef(3, "acrobat-1")),
        )
        assertTrue(changes.isEmpty)
    }
}
