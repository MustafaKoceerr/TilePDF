package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PdfPasswordException
import java.io.FileNotFoundException
import java.io.IOException

/**
 * What the reader tells the user when a document can't be opened; `null` for
 * errors that are bugs rather than a problem with the file.
 */
internal fun readerErrorFor(error: Throwable): ReaderError? = when (error) {
    // Before IOException: the password error is one.
    is PdfPasswordException -> ReaderError.PasswordProtected
    // A revoked SAF grant leaves the file just as unreachable as a deleted one.
    is FileNotFoundException, is SecurityException -> ReaderError.NotFound
    is IOException -> ReaderError.Corrupt
    else -> null
}
