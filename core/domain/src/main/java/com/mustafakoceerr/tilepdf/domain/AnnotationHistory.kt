package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef

/** A document's annotations as the user sees them. */
data class AnnotationState(val annotations: List<Annotation>, val removedForeign: Set<ForeignRef>)

/**
 * Undo and redo over [AnnotationState] snapshots. An edit that changes
 * nothing records nothing; a new edit drops what could be redone. Kept in
 * memory only: a crash keeps the annotations (in the draft), not the history.
 */
class AnnotationHistory(initial: AnnotationState, private val limit: Int = 100) {

    var state: AnnotationState = initial
        private set

    private val undoStack = ArrayDeque<AnnotationState>()
    private val redoStack = ArrayDeque<AnnotationState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun add(annotation: Annotation): AnnotationState = edit { it.copy(annotations = it.annotations + annotation) }

    fun remove(id: String): AnnotationState = edit { current ->
        current.copy(annotations = current.annotations.filterNot { it.id == id })
    }

    fun removeForeign(ref: ForeignRef): AnnotationState = edit { it.copy(removedForeign = it.removedForeign + ref) }

    /** Removes several of ours and other apps' annotations as one step: an eraser gesture. */
    fun removeAll(ids: Set<String>, refs: Set<ForeignRef>): AnnotationState = edit { current ->
        AnnotationState(current.annotations.filterNot { it.id in ids }, current.removedForeign + refs)
    }

    /**
     * Removes [removed] and adds [added] as one step: how a markup is
     * toggled off part of its text, leaving the rest as new markups.
     */
    fun swap(removed: Set<String>, added: List<Annotation>): AnnotationState = edit { current ->
        current.copy(annotations = current.annotations.filterNot { it.id in removed } + added)
    }

    /** Swaps annotation [old] for [new] in place, as one step: how a note is edited. */
    fun replace(old: String, new: Annotation): AnnotationState = edit { current ->
        current.copy(annotations = current.annotations.map { if (it.id == old) new else it })
    }

    fun undo(): AnnotationState {
        val previous = undoStack.removeLastOrNull() ?: return state
        redoStack.addLast(state)
        state = previous
        return state
    }

    fun redo(): AnnotationState {
        val next = redoStack.removeLastOrNull() ?: return state
        undoStack.addLast(state)
        state = next
        return state
    }

    /** Starts over from [state] with no history, e.g. after a save. */
    fun reset(state: AnnotationState): AnnotationState {
        undoStack.clear()
        redoStack.clear()
        this.state = state
        return state
    }

    private fun edit(change: (AnnotationState) -> AnnotationState): AnnotationState {
        val next = change(state)
        if (next == state) return state
        undoStack.addLast(state)
        if (undoStack.size > limit) undoStack.removeFirst()
        redoStack.clear()
        state = next
        return state
    }
}
