package com.mustafakoceerr.tilepdf.data.signature

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.AppearanceLine
import com.mustafakoceerr.tilepdf.core.pdf.SignatureAppearance
import com.mustafakoceerr.tilepdf.model.Signature
import com.mustafakoceerr.tilepdf.model.placedAround

private const val PADDING_PT = 4f
private const val GAP_PT = 4f
private const val MAX_TEXT_PT = 11f

/** Noto Sans's characters are about this wide, in ems, on average. */
private const val CHAR_WIDTH_EM = 0.6f

/** How much of its line a line's text is tall, leaving room between lines. */
private const val TEXT_OF_LINE = 0.75f

/**
 * Lays out a signature box: with a [drawing], the drawing on the left half
 * and [lines] on the right; without, the lines across. Each line shrinks to
 * fit its width.
 */
fun signatureAppearance(rect: PdfRect, lines: List<String>, drawing: Signature?): SignatureAppearance {
    val inner = PdfRect(rect.left + PADDING_PT, rect.top + PADDING_PT, rect.right - PADDING_PT, rect.bottom - PADDING_PT)
    val split = rect.left + rect.width / 2
    val textLeft = if (drawing != null) split + GAP_PT / 2 else inner.left
    val strokes = drawing?.let { fit(it, PdfRect(inner.left, inner.top, split - GAP_PT / 2, inner.bottom)) }.orEmpty()

    val lineHeight = inner.height / lines.size.coerceAtLeast(1)
    val textWidth = inner.right - textLeft
    val laidOut = lines.map(::plainSpaces).mapIndexed { i, text ->
        val size = minOf(MAX_TEXT_PT, lineHeight * TEXT_OF_LINE, textWidth / (CHAR_WIDTH_EM * text.length.coerceAtLeast(1)))
        AppearanceLine(text, textLeft, inner.top + lineHeight * i + lineHeight / 2 + size * 0.35f, size)
    }
    return SignatureAppearance(rect, laidOut, strokes)
}

/**
 * [text] with every kind of space as a plain one: Android writes times with
 * a narrow no-break space, which the stamp's font has no glyph for.
 */
private fun plainSpaces(text: String): String = text.map { if (it.isWhitespace()) ' ' else it }.joinToString("")

/** [signature] as large as fits in [area], centred. */
private fun fit(signature: Signature, area: PdfRect): List<List<PdfPoint>> {
    val width = minOf(area.width, area.height / signature.height.coerceAtLeast(0.01f))
    val centre = PdfPoint((area.left + area.right) / 2, (area.top + area.bottom) / 2)
    return signature.placedAround(centre, width)
}
