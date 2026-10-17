package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.AnnotationDraft
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.flow.Flow

/** The current version of a file, or null if it can't be read. */
fun interface FileStamps {
    suspend fun stamp(uri: String): FileStamp?
}

/**
 * Stands for the version of a file whose provider won't tell. Every such
 * file compares equal to it, so other apps' changes go unnoticed there, but
 * saving still works.
 */
internal val UNKNOWN_STAMP = FileStamp(size = -1, lastModified = null)

internal suspend fun FileStamps.stampOrUnknown(uri: String): FileStamp = stamp(uri) ?: UNKNOWN_STAMP

/** Each document's annotation draft: what TilePDF changed until it is in the file. */
interface AnnotationRepository {

    fun observe(uri: String): Flow<AnnotationDraft?>

    /**
     * The draft to edit [uri] with, read from [document] when there is none.
     * A clean draft of a file that changed elsewhere is read again; a dirty
     * one is kept and marked [SaveError.FileChanged].
     */
    suspend fun prepare(uri: String, document: PdfDocument): AnnotationDraft

    /** Replaces the draft's content; it now needs saving. */
    suspend fun update(uri: String, annotations: List<Annotation>, removedForeign: Set<ForeignRef>)

    /**
     * Records that [saved] is now in the file, whose version is [newBase].
     * Edits made since [saved] was read stay unsaved.
     */
    suspend fun markSaved(uri: String, saved: AnnotationDraft, newBase: FileStamp)

    suspend fun markFailed(uri: String, error: SaveError)

    /**
     * Moves the draft to [to], a copy holding [saved] at version [newBase].
     * [to] must already be in the library.
     */
    suspend fun moveTo(from: String, to: String, saved: AnnotationDraft, newBase: FileStamp)

    /** Documents whose draft isn't fully in the file yet. */
    suspend fun dirtyUris(): List<String>
}
