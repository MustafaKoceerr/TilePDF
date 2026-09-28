package com.mustafakoceerr.tilepdf.data.document

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument

/** Access to PDF files picked through the Storage Access Framework. */
interface DocumentRepository {

    /** Keeps read access to [uri] across app restarts, when the provider allows it. */
    fun persistAccess(uri: String)

    /** Whether the app still holds read access to [uri]. */
    fun hasAccess(uri: String): Boolean

    /** Name shown to the user, e.g. `report.pdf`. */
    suspend fun displayName(uri: String): String?

    /**
     * Opens [uri] for rendering. The caller owns the result and must close it.
     *
     * @throws java.io.FileNotFoundException if the file is gone or access was revoked.
     * @throws SecurityException if the PDF is password protected.
     * @throws java.io.IOException if the file is not a valid PDF.
     */
    suspend fun open(uri: String): PdfDocument
}
