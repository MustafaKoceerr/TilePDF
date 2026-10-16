package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import javax.inject.Inject

/** A document ready to read, plus where the user left off. */
class OpenedDocument(
    val document: PdfDocument,
    /** The URI the document was opened from, which the library and positions use. */
    val uri: String,
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

    /**
     * The caller owns the returned document. If anything fails or the caller is
     * cancelled after the file was opened, the document is closed here.
     *
     * @throws Exception as documented on [DocumentRepository.open].
     */
    suspend operator fun invoke(uri: String, password: String? = null): OpenedDocument {
        // A URI the app can reopen later; possibly a private copy of the file.
        val prepared = documentRepository.prepare(uri)
        val document = documentRepository.open(prepared, password)
        try {
            val displayName = documentRepository.displayName(uri) ?: uri.substringAfterLast('/')
            libraryRepository.recordOpened(prepared, displayName, document.pageCount)
            return OpenedDocument(
                document = document,
                uri = prepared,
                displayName = displayName,
                position = libraryRepository.readingPosition(prepared) ?: ReadingPosition(),
            )
        } catch (e: Throwable) {
            document.close()
            throw e
        }
    }
}
