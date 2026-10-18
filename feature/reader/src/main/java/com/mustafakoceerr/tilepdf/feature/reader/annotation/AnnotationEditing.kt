package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Note

/** What a one-finger gesture on a page does while annotating. */
enum class AnnotationTool { Select, Pen, Eraser, Note }

/** A note being written: new at [at], or [editing] an existing one. */
data class NoteEditor(val pageIndex: Int, val at: PdfPoint, val editing: Note?)

/** The reader's annotation UI state, apart from the annotations themselves. */
data class AnnotationEditing(
    /** The annotation toolbar is shown. */
    val annotating: Boolean = false,
    val tool: AnnotationTool = AnnotationTool.Select,
    /** The annotation whose menu is open. */
    val menu: AnnotationTarget? = null,
    val noteEditor: NoteEditor? = null,
) {
    /** Pen and eraser take one-finger drags: no scrolling, no selection. */
    val drawing: Boolean get() = annotating && (tool == AnnotationTool.Pen || tool == AnnotationTool.Eraser)
}
