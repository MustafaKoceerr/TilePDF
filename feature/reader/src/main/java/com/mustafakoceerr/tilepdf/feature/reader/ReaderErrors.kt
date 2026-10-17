package com.mustafakoceerr.tilepdf.feature.reader

import androidx.annotation.StringRes
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
    is FileNotFoundException -> OpenFailure.Failed(ReaderError.NotFound)
    // A revoked SAF grant: the file is likely still there.
    is SecurityException -> OpenFailure.Failed(ReaderError.PermissionLost)
    is IOException -> OpenFailure.Failed(ReaderError.Corrupt)
    // A huge page or document; trying again after other apps close may work.
    is OutOfMemoryError -> OpenFailure.Failed(ReaderError.OutOfMemory)
    else -> null
}

/** What the error screen's buttons do. */
enum class ErrorAction { ChooseFile, Retry, Back }

/** An error screen's text and buttons. */
internal data class ErrorCopy(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
    val primary: ErrorAction,
    val secondary: ErrorAction?,
)

internal fun ReaderError.errorCopy(): ErrorCopy = when (this) {
    ReaderError.NotFound ->
        ErrorCopy(R.string.error_not_found_title, R.string.error_not_found_message, ErrorAction.ChooseFile, ErrorAction.Back)
    ReaderError.PermissionLost ->
        ErrorCopy(R.string.error_permission_title, R.string.error_permission_message, ErrorAction.ChooseFile, ErrorAction.Back)
    ReaderError.Corrupt ->
        ErrorCopy(R.string.error_corrupt_title, R.string.error_corrupt_message, ErrorAction.Back, null)
    ReaderError.OutOfMemory ->
        ErrorCopy(R.string.error_memory_title, R.string.error_memory_message, ErrorAction.Retry, ErrorAction.Back)
}
