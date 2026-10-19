package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.model.OcrWord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TextRecognitionTest {

    private fun words(text: String) =
        listOf(OcrWord(text, listOf(PdfPoint(0f, 0f), PdfPoint(9f, 0f), PdfPoint(9f, 4f), PdfPoint(0f, 4f))))

    @Test
    fun thisPageIsTheCurrentOneWhenItNeedsText() = runTest {
        assertEquals(listOf(4), pagesToRecognize(allPages = false, current = 4, pageCount = 9) { true })
        assertEquals(emptyList<Int>(), pagesToRecognize(allPages = false, current = 4, pageCount = 9) { false })
    }

    @Test
    fun allPagesAreThoseWithoutText() = runTest {
        val withText = setOf(0, 2)

        assertEquals(listOf(1, 3), pagesToRecognize(allPages = true, current = 0, pageCount = 4) { it !in withText })
    }

    @Test
    fun nothingToRecognizeSaysTheTextIsAlreadyThere() {
        // Not "no text found": nothing was looked at, the pages have text.
        assertEquals(RecognitionResult.AlreadyHasText(allPages = false), recognitionResult(allPages = false, pagesTried = 0, pagesWithText = 0))
        assertEquals(RecognitionResult.AlreadyHasText(allPages = true), recognitionResult(allPages = true, pagesTried = 0, pagesWithText = 0))
    }

    @Test
    fun recognizedPagesAreCounted() {
        assertEquals(RecognitionResult.Done(pagesWithText = 2), recognitionResult(allPages = true, pagesTried = 3, pagesWithText = 2))
        assertEquals(RecognitionResult.Done(pagesWithText = 0), recognitionResult(allPages = false, pagesTried = 1, pagesWithText = 0))
    }

    @Test
    fun scaleKeepsTheLongSideAt2500PixelsAtMost4PerPoint() {
        assertEquals(2500f / 842f, recognitionScale(PageSize(595, 842)), 0.0001f)
        assertEquals(4f, recognitionScale(PageSize(200, 300)), 0.0001f)
    }

    @Test
    fun keepsEveryPageWithWordsAndCountsProgress() = runTest {
        val kept = mutableListOf<Pair<Int, List<OcrWord>>>()
        val progress = mutableListOf<RecognitionProgress>()

        recognizePages(
            pages = listOf(1, 3, 5),
            read = { page -> if (page == 3) emptyList() else words("sayfa$page") },
            keep = { page, found -> kept += page to found },
            onProgress = { progress += it },
        )

        assertEquals(listOf(1 to words("sayfa1"), 5 to words("sayfa5")), kept)
        assertEquals(
            listOf(RecognitionProgress(0, 3), RecognitionProgress(1, 3), RecognitionProgress(2, 3), RecognitionProgress(3, 3)),
            progress,
        )
    }

    @Test
    fun cancellingKeepsTheFinishedPagesAndNoHalfPage() = runTest {
        val kept = mutableListOf<Int>()
        val keepingPage1 = CompletableDeferred<Unit>()
        val releasePage1 = CompletableDeferred<Unit>()

        val job = launch {
            recognizePages(
                pages = listOf(0, 1, 2),
                read = { page -> words("sayfa$page") },
                keep = { page, _ ->
                    if (page == 1) {
                        keepingPage1.complete(Unit)
                        releasePage1.await()
                    }
                    kept += page
                },
                onProgress = {},
            )
        }
        keepingPage1.await()
        job.cancel()
        releasePage1.complete(Unit)
        advanceUntilIdle()

        // Page 1 was being kept when cancelled: it is finished, page 2 never starts.
        assertEquals(listOf(0, 1), kept)
    }
}
