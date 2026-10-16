package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import javax.inject.Inject

/** A document ready to read, plus where the user left off. */
class OpenedDocument(
    val document: PdfDocument,
    val displayName: String,
    val position: ReadingPosition,
)

/**
 * Opens a PDF for reading. Opening a document also records it in the library
 * and restores the last reading position.
 */
class OpenDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val libraryRepository: LibraryRepository,
) {

    /** @throws Exception as documented on [DocumentRepository.open]. */
    suspend operator fun invoke(uri: String): OpenedDocument {
        val document = documentRepository.open(uri)
        val displayName = documentRepository.displayName(uri) ?: uri.substringAfterLast('/')
        libraryRepository.recordOpened(uri, displayName, document.pageCount)
        return OpenedDocument(
            document = document,
            displayName = displayName,
            position = libraryRepository.readingPosition(uri) ?: ReadingPosition(),
        )
    }
}
