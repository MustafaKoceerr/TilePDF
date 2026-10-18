package com.mustafakoceerr.tilepdf.core.pdf

import android.net.Uri

/** Opens PDFs. The only implementation is [Pdfium]; tests use fakes. */
interface PdfEngine {
    /**
     * @throws java.io.FileNotFoundException if [uri] can't be opened.
     * @throws SecurityException if the read grant for [uri] was revoked.
     * @throws PdfPasswordException if the document needs a (different) password.
     * @throws PdfCorruptException if the file isn't a readable PDF.
     */
    suspend fun open(uri: Uri, password: String? = null): PdfDocument

    /**
     * Opens [uri] to change its annotations and save a copy, with the same
     * exceptions as [open]. The returned document draws nothing.
     */
    suspend fun openForEditing(uri: Uri, password: String? = null): EditablePdfDocument
}
