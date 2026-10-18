package com.mustafakoceerr.tilepdf.core.ocr

import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.OcrWord

/** A word as the recogniser read it: its corners in reading order, in bitmap pixels. */
data class PixelWord(val text: String, val corners: List<PdfPoint>)

/**
 * The page words of [words], read from a bitmap of the page shown through
 * [view] at [scale] pixels per point. Words without text or four corners are
 * dropped.
 */
fun recognizedWords(words: List<PixelWord>, scale: Float, view: PageViewTransform): List<OcrWord> =
    words.filter { it.text.isNotBlank() && it.corners.size == 4 }
        .map { word -> OcrWord(word.text, word.corners.map { view.toPage(PdfPoint(it.x / scale, it.y / scale)) }) }
