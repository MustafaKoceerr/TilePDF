package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Deletes private copies that never made it into the library, such as a shared
 * PDF whose password prompt was cancelled, and copies left half-written.
 */
class CleanUpCopiesUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val libraryRepository: LibraryRepository,
) {
    suspend operator fun invoke() {
        val inLibrary = libraryRepository.observeDocuments().first().mapTo(HashSet()) { it.uri }
        documentRepository.forgetCopiesExcept(inLibrary)
    }
}
