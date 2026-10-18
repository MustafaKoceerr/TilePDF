package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.XfaEdit

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

    /** Whether this is an XFA form whose pages the engine lays out: its fields aren't widgets. */
    suspend fun isXfa(): Boolean = false

    /**
     * Taps [at] on [pageIndex] of an XFA form as a finger would. Returns the
     * text of the field it focused if that's a text field, else null.
     */
    suspend fun xfaTap(pageIndex: Int, at: PdfPoint): String? = null

    /**
     * Replaces the text of the XFA field the last tap focused.
     * @throws PdfWriteException if the engine refused it.
     */
    suspend fun xfaWrite(pageIndex: Int, text: String) {
        throw PdfWriteException("This document has no XFA form")
    }

    /**
     * Lets [host] answer the form's scripts; without one they get default
     * answers. With [openAction], the document's open action runs, as when
     * a reader first opens it.
     */
    suspend fun setScriptHost(host: FormScriptHost?, openAction: Boolean = false) = Unit
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

/**
 * Fills in an XFA form again from its [edits], in order: each tap, then its
 * text written into the field the tap focused.
 * @throws PdfWriteException if a text edit's tap focused no text field, or
 *   the engine refused the text.
 */
suspend fun FillableForm.replay(edits: List<XfaEdit>) {
    for (edit in edits) {
        val focused = xfaTap(edit.pageIndex, edit.at)
        val text = edit.text ?: continue
        if (focused == null) throw PdfWriteException("No XFA text field at ${edit.at} on page ${edit.pageIndex}")
        xfaWrite(edit.pageIndex, text)
    }
}
