package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.model.OcrWord

/**
 * Words TilePDF recognised on scanned pages, kept in each page as invisible
 * text: the page draws as before, and its text can be searched and copied.
 */
interface RecognizedTextPages {

    /** Whether [pageIndex] already holds text TilePDF recognised. */
    suspend fun hasRecognizedText(pageIndex: Int): Boolean = false

    /**
     * Adds [words] to [pageIndex] as invisible text.
     * @throws PdfWriteException if the engine refused them.
     */
    suspend fun addRecognizedText(pageIndex: Int, words: List<OcrWord>) {
        if (words.isNotEmpty()) throw PdfWriteException("This document can't take recognised text")
    }
}

/**
 * Adds the words of each page in [pages] that doesn't hold recognised text
 * yet, and returns those pages. Pages without words are left alone.
 */
suspend fun RecognizedTextPages.addMissingRecognizedText(pages: Map<Int, List<OcrWord>>): Set<Int> {
    val added = sortedSetOf<Int>()
    for ((pageIndex, words) in pages) {
        if (words.isEmpty() || hasRecognizedText(pageIndex)) continue
        addRecognizedText(pageIndex, words)
        added += pageIndex
    }
    return added
}
