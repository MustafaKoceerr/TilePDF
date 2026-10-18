package com.mustafakoceerr.tilepdf.model

/** A PDF the user has opened before, as shown in the library. */
data class LibraryDocument(
    /** SAF content URI; also the document's identity. */
    val uri: String,
    val displayName: String,
    val pageCount: Int,
    val position: ReadingPosition,
    val lastOpenedAt: Long,
)
