package com.mustafakoceerr.tilepdf.core.pdf

import java.io.IOException

/** Why a PDF that could be read from storage still couldn't be opened. */
sealed class PdfOpenException(message: String) : IOException(message)

/** The document is encrypted; [wrongPassword] is false when none was given. */
class PdfPasswordException(val wrongPassword: Boolean) :
    PdfOpenException(if (wrongPassword) "Wrong password" else "Password required")

/** Not a PDF, damaged, truncated, or using an unsupported security handler. */
class PdfCorruptException(message: String) : PdfOpenException(message)
