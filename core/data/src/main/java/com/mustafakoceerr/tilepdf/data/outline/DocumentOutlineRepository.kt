package com.mustafakoceerr.tilepdf.data.outline

import com.mustafakoceerr.tilepdf.model.OutlineEntry

/** A document's table of contents (the PDF "outline" or bookmarks). */
interface DocumentOutlineRepository {

    /** Entries in reading order; empty if the document has no outline. */
    suspend fun outline(uri: String): List<OutlineEntry>
}
