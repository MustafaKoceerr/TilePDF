package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.pdf.EditablePdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import java.io.File
import java.io.FileNotFoundException
import java.io.OutputStream

/** The files a save touches: the document, a temp copy, and the engine on both. */
interface AnnotationFiles : FileStamps {

    /** @throws Exception as [com.mustafakoceerr.tilepdf.core.pdf.PdfEngine.openForEditing]. */
    suspend fun openForEditing(uri: String, password: String?): EditablePdfDocument

    /** Opens a saved temp [file] to check it; the caller closes it. */
    suspend fun openForReading(file: File, password: String?): PdfDocument

    /** A new empty file for one save, private to the app. */
    fun newTempFile(): File

    /**
     * Overwrites [uri] with the bytes of [with]; not cancellable once started.
     * @throws SecurityException or [FileNotFoundException] if [uri] can't be written.
     */
    suspend fun replace(uri: String, with: File)
}

/**
 * Opens a stream that overwrites a file from the start. "wt" truncates;
 * providers that reject it get "w", which may not truncate. That is still
 * safe here, because an incremental save is never shorter than the file it
 * replaces.
 */
internal fun openForOverwrite(open: (mode: String) -> OutputStream?): OutputStream {
    val truncating = try {
        open("wt")
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: FileNotFoundException) {
        null
    }
    return truncating ?: open("w") ?: throw FileNotFoundException("No stream to overwrite")
}
