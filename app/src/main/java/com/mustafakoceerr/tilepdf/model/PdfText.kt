package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect

/**
 * One character and its box in PDF point space (top-left origin, crop box
 * applied, page rotation applied). Every text provider produces this format,
 * so coordinate mapping never depends on where the text came from.
 */
data class TextChar(val char: Char, val box: PdfRect)

data class PageText(val pageIndex: Int, val chars: List<TextChar>)

/** One search hit; [rects] are per-line boxes in PDF point space. */
data class SearchMatch(val pageIndex: Int, val rects: List<PdfRect>)
