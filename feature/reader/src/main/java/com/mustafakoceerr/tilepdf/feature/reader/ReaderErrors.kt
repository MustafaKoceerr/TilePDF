package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PdfPasswordException
import java.io.FileNotFoundException
import java.io.IOException

/** Why a document didn't open, as the reader presents it. */
internal sealed interface OpenFailure {
    /** Ask for the password; [wrongPassword] when one was already tried. */
    data class NeedsPassword(val wrongPassword: Boolean) : OpenFailure

    data class Failed(val reason: ReaderError) : OpenFailure
}

/** `null` for errors that are bugs rather than a problem with the file. */
internal fun openFailureFor(error: Throwable): OpenFailure? = when (error) {
    // Before IOException: the password error is one.
    is PdfPasswordException -> OpenFailure.NeedsPassword(error.wrongPassword)
    // A revoked SAF grant leaves the file just as unreachable as a deleted one.
    is FileNotFoundException, is SecurityException -> OpenFailure.Failed(ReaderError.NotFound)
    is IOException -> OpenFailure.Failed(ReaderError.Corrupt)
    else -> null
}
