package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaving
import com.mustafakoceerr.tilepdf.data.annotation.SaveResult
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import javax.inject.Inject

/**
 * Writes the annotated document [uri] into [target], a new file the user
 * picked, and makes the copy the annotated document: it joins the library
 * and takes over the draft. Used when [uri] itself can't be written.
 */
class SaveAsCopyUseCase @Inject constructor(
    private val saver: AnnotationSaving,
    private val library: LibraryRepository,
    private val documents: DocumentRepository,
) {
    suspend operator fun invoke(uri: String, target: String, displayName: String, pageCount: Int): SaveResult {
        documents.persistAccess(target)
        // The draft moves under the copy, which needs a library entry first.
        library.recordOpened(target, displayName, pageCount)
        val result = saver.saveCopy(uri, target)
        if (result !is SaveResult.Saved) library.remove(target)
        return result
    }
}
