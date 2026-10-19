package com.mustafakoceerr.tilepdf.core.pdf

import java.io.IOException
import java.io.InputStream

/**
 * The font PDFium draws form field letters with when the field's own font
 * lacks them, such as Turkish İ, ş and ğ in a Helvetica field: a Noto Sans
 * subset covering Latin, Greek and Cyrillic.
 */
internal object FormFallbackFont {
    const val ASSET = "fonts/NotoSans-TilePdf.ttf"

    /** The font's bytes from [open]; null if it can't be read, so forms work as without it. */
    fun read(open: (String) -> InputStream): ByteArray? =
        try {
            open(ASSET).use { it.readBytes() }
        } catch (_: IOException) {
            null
        }
}
