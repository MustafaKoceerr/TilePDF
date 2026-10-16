package com.mustafakoceerr.tilepdf.data.document

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument

/** Access to PDF files picked through the Storage Access Framework. */
interface DocumentRepository {

    /** Keeps read access to [uri] across app restarts, when the provider allows it. */
    fun persistAccess(uri: String)

    /**
     * A URI for [uri] the app can open now and reopen after a restart: [uri]
     * itself when its provider reports a size and a read grant is (or can be)
     * persisted, otherwise a private copy of the file.
     *
     * @throws java.io.FileNotFoundException if [uri] can't be read.
     * @throws java.io.IOException if copying fails; no partial copy is left.
     */
    suspend fun prepare(uri: String): String

    /** Deletes the private copy behind [uri], if it is one; never a user's file. */
    suspend fun forget(uri: String)

    /** URIs the app can still open: persisted read grants and private copies. */
    suspend fun accessibleUris(): Set<String>

    /** Name shown to the user, e.g. `report.pdf`. */
    suspend fun displayName(uri: String): String?

    /**
     * Opens [uri] for reading. The caller owns the result and must close it.
     *
     * @throws java.io.FileNotFoundException if the file is gone.
     * @throws SecurityException if the read grant was revoked.
     * @throws com.mustafakoceerr.tilepdf.core.pdf.PdfPasswordException if a (different) password is needed.
     * @throws com.mustafakoceerr.tilepdf.core.pdf.PdfCorruptException if the file is not a readable PDF.
     */
    suspend fun open(uri: String, password: String? = null): PdfDocument
}
