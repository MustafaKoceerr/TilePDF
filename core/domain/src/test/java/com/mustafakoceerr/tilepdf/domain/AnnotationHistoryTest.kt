package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationHistoryTest {

    private fun note(id: String, text: String = ""): Annotation = Note(id, 0, 0xFFFFEB3B.toInt(), PdfPoint(1f, 1f), text)

    private val empty = AnnotationState(emptyList(), emptySet())

    private fun AnnotationHistory.ids() = state.annotations.map { it.id }

    @Test
    fun addThenUndoRedo() {
        val history = AnnotationHistory(empty)
        history.add(note("a"))
        history.add(note("b"))
        assertEquals(listOf("a", "b"), history.ids())

        history.undo()
        assertEquals(listOf("a"), history.ids())
        assertTrue(history.canRedo)

        history.redo()
        assertEquals(listOf("a", "b"), history.ids())
        assertFalse(history.canRedo)
    }

    @Test
    fun removeIsUndoable() {
        val history = AnnotationHistory(AnnotationState(listOf(note("a"), note("b")), emptySet()))
        history.remove("a")
        assertEquals(listOf("b"), history.ids())
        history.undo()
        assertEquals(listOf("a", "b"), history.ids())
    }

    @Test
    fun removeForeignIsUndoable() {
        val history = AnnotationHistory(empty)
        val ref = ForeignRef(0, "#2")
        history.removeForeign(ref)
        assertEquals(setOf(ref), history.state.removedForeign)
        history.undo()
        assertEquals(emptySet<ForeignRef>(), history.state.removedForeign)
    }

    @Test
    fun swapIsOneStep() {
        // Toggling a markup off part of a line removes it and adds back the rest.
        val history = AnnotationHistory(AnnotationState(listOf(note("a"), note("b")), emptySet()))
        history.swap(removed = setOf("a"), added = listOf(note("c"), note("d")))
        assertEquals(listOf("b", "c", "d"), history.ids())

        history.undo()
        assertEquals(listOf("a", "b"), history.ids())
        assertFalse(history.canUndo)
    }

    @Test
    fun aSwapThatChangesNothingIsNotRecorded() {
        val history = AnnotationHistory(AnnotationState(listOf(note("a")), emptySet()))
        history.swap(removed = emptySet(), added = emptyList())
        assertFalse(history.canUndo)
    }

    @Test
    fun replaceIsOneStep() {
        val history = AnnotationHistory(AnnotationState(listOf(note("a", "old")), emptySet()))
        history.replace("a", note("a2", "new"))
        assertEquals(listOf("a2"), history.ids())
        history.undo()
        assertEquals(listOf("a"), history.ids())
        assertFalse(history.canUndo)
    }

    @Test
    fun newEditClearsRedo() {
        val history = AnnotationHistory(empty)
        history.add(note("a"))
        history.undo()
        history.add(note("b"))
        assertFalse(history.canRedo)
        assertEquals(listOf("b"), history.ids())
    }

    @Test
    fun unknownRemoveRecordsNothing() {
        val history = AnnotationHistory(empty)
        history.remove("missing")
        history.removeForeign(ForeignRef(0, "x"))
        history.removeForeign(ForeignRef(0, "x"))
        history.undo()
        assertFalse(history.canUndo)
        assertEquals(emptySet<ForeignRef>(), history.state.removedForeign)
    }

    @Test
    fun limitDropsOldest() {
        val history = AnnotationHistory(empty, limit = 2)
        history.add(note("a"))
        history.add(note("b"))
        history.add(note("c"))
        history.undo()
        history.undo()
        assertFalse(history.canUndo)
        assertEquals(listOf("a"), history.ids())
    }

    @Test
    fun resetClearsHistory() {
        val history = AnnotationHistory(empty)
        history.add(note("a"))
        history.undo()
        val state = AnnotationState(listOf(note("z")), emptySet())
        history.reset(state)
        assertEquals(state, history.state)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun removeAllIsOneStep() {
        val history = AnnotationHistory(AnnotationState(listOf(note("a"), note("b"), note("c")), emptySet()))
        history.removeAll(setOf("a", "c"), setOf(ForeignRef(1, "x")))
        assertEquals(listOf("b"), history.ids())
        assertEquals(setOf(ForeignRef(1, "x")), history.state.removedForeign)
        history.undo()
        assertEquals(listOf("a", "b", "c"), history.ids())
        assertFalse(history.canUndo)
    }

    @Test
    fun removeAllOfNothingRecordsNothing() {
        val history = AnnotationHistory(AnnotationState(listOf(note("a")), emptySet()))
        history.removeAll(setOf("missing"), emptySet())
        assertFalse(history.canUndo)
    }
}
