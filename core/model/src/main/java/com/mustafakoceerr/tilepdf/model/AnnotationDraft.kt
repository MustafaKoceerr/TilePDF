package com.mustafakoceerr.tilepdf.model

/** What identifies a version of a file: its size and, when the provider tells, last change. */
data class FileStamp(val size: Long, val lastModified: Long?)

/** Why the last save of a draft didn't reach the file. */
enum class SaveError { NoWritePermission, FileChanged, NeedsPassword, Failed }

/**
 * Everything TilePDF changed in one document, kept until it is in the file.
 *
 * @param annotations all of TilePDF's annotations in the document, saved or not.
 * @param removedForeign other apps' annotations the user deleted, not yet saved.
 * @param dirty whether the file lacks some of this.
 * @param base the file version [removedForeign] keys and the saved state refer to.
 */
data class AnnotationDraft(
    val uri: String,
    val annotations: List<Annotation>,
    val removedForeign: Set<ForeignRef>,
    val dirty: Boolean,
    val base: FileStamp,
    val saveError: SaveError?,
)
