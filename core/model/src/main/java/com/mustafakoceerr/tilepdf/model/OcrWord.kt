package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint

/**
 * A word recognised on a scanned page, in top-left page points. [quad] holds
 * its corners in reading order: top-left, top-right, bottom-right and
 * bottom-left of the word as read, so a word on a sideways scan has a turned
 * quad.
 */
data class OcrWord(val text: String, val quad: List<PdfPoint>)
