package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.model.FormField

/** A document's AcroForm. A document without one has no fields and takes no edits. */
interface FillableForm {

    val pageCount: Int

    /** Whether the document has a form TilePDF can fill. */
    suspend fun hasForm(): Boolean = false

    /** The fillable widgets on [pageIndex]. */
    suspend fun formFields(pageIndex: Int): List<FormField> = emptyList()

    /**
     * Makes the changes as a user filling the form would, so each widget
     * gets a new appearance. False if the engine refused one.
     */
    suspend fun applyFormEdits(edits: List<FormEdit>): Boolean = edits.isEmpty()
}

/**
 * Makes the form hold [values] (field name → `/V`) and returns the pages
 * whose widgets changed.
 * @throws PdfWriteException if the engine refused a change.
 */
suspend fun FillableForm.fill(values: Map<String, String>): Set<Int> {
    if (values.isEmpty() || !hasForm()) return emptySet()
    val edits = formEdits(allFormFields(), values)
    if (edits.isEmpty()) return emptySet()
    if (!applyFormEdits(edits)) throw PdfWriteException("A form field refused its new value")
    return edits.mapTo(sortedSetOf()) { it.pageIndex }
}

/** Every fillable widget of the document; empty without a form. */
suspend fun FillableForm.allFormFields(): List<FormField> =
    if (hasForm()) (0 until pageCount).flatMap { formFields(it) } else emptyList()
