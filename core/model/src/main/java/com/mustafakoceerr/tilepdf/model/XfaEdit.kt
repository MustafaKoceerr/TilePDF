package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint

/**
 * One step of filling in an XFA form: a tap at [at] on [pageIndex], in
 * top-left points, and the [text] then written into the field it focused, if
 * any. XFA values can't be read or set by name, so a form is filled again by
 * replaying its edits in order: the engine is deterministic.
 */
data class XfaEdit(val pageIndex: Int, val at: PdfPoint, val text: String? = null)
