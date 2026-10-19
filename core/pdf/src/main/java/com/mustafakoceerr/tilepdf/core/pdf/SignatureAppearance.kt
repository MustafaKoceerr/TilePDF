package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect

/** A line of text in a signature box: left edge [x], [baseline] and font [size], in top-left points. */
data class AppearanceLine(val text: String, val x: Float, val baseline: Float, val size: Float)

/** What a visible signature shows in [rect]: lines of text and a drawn signature's strokes, in top-left points. */
data class SignatureAppearance(
    val rect: PdfRect,
    val lines: List<AppearanceLine>,
    val strokes: List<List<PdfPoint>>,
    val strokeWidth: Float = 1f,
)
