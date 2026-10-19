package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.model.OcrWord
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The recogniser reads small print well from this many pixels on a page's long side. */
private const val RECOGNITION_LONG_SIDE_PX = 2500f

/** Small pages aren't blown up further than this many pixels per point. */
private const val RECOGNITION_MAX_SCALE = 4f

/** How far text recognition got: [done] of [total] pages. */
data class RecognitionProgress(val done: Int, val total: Int)

/**
 * The pages to recognise: the [current] one, or with [allPages] every page;
 * either way only those that [needText].
 */
internal suspend fun pagesToRecognize(
    allPages: Boolean,
    current: Int,
    pageCount: Int,
    needText: suspend (pageIndex: Int) -> Boolean,
): List<Int> {
    val candidates = if (allPages) 0 until pageCount else listOf(current).filter { it in 0 until pageCount }
    return candidates.filter { needText(it) }
}

/** How a run went that recognised [pagesTried] pages, [pagesWithText] of them with words. */
internal fun recognitionResult(allPages: Boolean, pagesTried: Int, pagesWithText: Int): RecognitionResult =
    if (pagesTried == 0) RecognitionResult.AlreadyHasText(allPages) else RecognitionResult.Done(pagesWithText)

/** Pixels per point to render [page] at for recognition. */
internal fun recognitionScale(page: PageSize): Float =
    minOf(RECOGNITION_LONG_SIDE_PX / maxOf(page.widthPt, page.heightPt), RECOGNITION_MAX_SCALE)

/**
 * Recognises [pages] one at a time: [read] finds a page's words, and [keep]
 * keeps those of each page that has some. A page being kept is finished
 * even if the work is cancelled meanwhile, so no page is ever half kept.
 */
internal suspend fun recognizePages(
    pages: List<Int>,
    read: suspend (pageIndex: Int) -> List<OcrWord>,
    keep: suspend (pageIndex: Int, words: List<OcrWord>) -> Unit,
    onProgress: (RecognitionProgress) -> Unit,
) {
    onProgress(RecognitionProgress(0, pages.size))
    pages.forEachIndexed { index, page ->
        currentCoroutineContext().ensureActive()
        val words = read(page)
        if (words.isNotEmpty()) withContext(NonCancellable) { keep(page, words) }
        onProgress(RecognitionProgress(index + 1, pages.size))
    }
}
