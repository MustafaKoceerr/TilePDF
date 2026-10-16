package com.mustafakoceerr.tilepdf.model

/**
 * One line of a document's table of contents, flattened from the outline
 * tree; [depth] is 0 for top-level chapters.
 */
data class OutlineEntry(
    val title: String,
    val pageIndex: Int,
    /**
     * Where on the page the entry points, in top-left PDF points, when the
     * document says so; `null` means the top of the page.
     */
    val topPt: Float?,
    val depth: Int,
)
