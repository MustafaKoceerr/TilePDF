package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * A PDF opened to change its annotations and save it. Nothing is hidden
 * here, unlike in a reader's [PdfDocument], so it can be saved safely.
 * Calls after [close] throw [IllegalStateException].
 */
interface EditablePdfDocument : Closeable, FillableForm, RecognizedTextPages {
    override val pageCount: Int

    /** `/NM` of TilePDF's annotations on [pageIndex]. */
    suspend fun ownAnnotationIds(pageIndex: Int): Set<String>

    /** Keys of the other apps' deletable annotations on [pageIndex]. */
    suspend fun foreignKeys(pageIndex: Int): Set<String>

    /**
     * Removes, then adds, then gives every touched page's new annotations an
     * appearance stream.
     * @throws PdfWriteException if PDFium rejects a change.
     */
    suspend fun apply(changes: AnnotationChanges)

    /**
     * Adds a stamp annotation named [name] on [pageIndex] that shows
     * [appearance]; signing turns it into the signature field.
     * @throws PdfWriteException if the engine refused it.
     */
    suspend fun addSignatureStamp(pageIndex: Int, name: String, appearance: SignatureAppearance)

    /**
     * Writes the original bytes followed by the changes to [target].
     * @throws PdfWriteException if writing fails.
     */
    suspend fun saveIncremental(target: File)
}

class PdfWriteException(message: String) : IOException(message)

/**
 * Makes the file hold exactly [wanted] of TilePDF's annotations and drops the
 * foreign ones in [removedForeign]. Returns false, changing nothing, if the
 * file already matches.
 */
suspend fun EditablePdfDocument.write(wanted: List<Annotation>, removedForeign: Set<ForeignRef>): Boolean {
    val existing = (0 until pageCount).associateWith { ownAnnotationIds(it) }.filterValues { it.isNotEmpty() }
    val existingForeign = removedForeign.map { it.pageIndex }.distinct()
        .filter { it in 0 until pageCount }
        .associateWith { foreignKeys(it) }
    val changes = annotationChanges(existing, wanted, existingForeign, removedForeign)
    if (changes.isEmpty) return false
    apply(changes)
    return true
}
