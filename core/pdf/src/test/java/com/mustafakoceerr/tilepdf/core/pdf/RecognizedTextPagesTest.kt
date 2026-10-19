package com.mustafakoceerr.tilepdf.core.pdf

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.OcrWord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizedTextPagesTest {

    private val quad = listOf(PdfPoint(0f, 0f), PdfPoint(10f, 0f), PdfPoint(10f, 5f), PdfPoint(0f, 5f))
    private fun words(vararg texts: String) = texts.map { OcrWord(it, quad) }

    private class FakePages(private val recognized: MutableSet<Int>) : RecognizedTextPages {
        val added = mutableMapOf<Int, List<OcrWord>>()
        override suspend fun hasRecognizedText(pageIndex: Int) = pageIndex in recognized
        override suspend fun addRecognizedText(pageIndex: Int, words: List<OcrWord>) {
            added[pageIndex] = words
            recognized += pageIndex
        }
    }

    @Test
    fun addsOnlyPagesTheDocumentLacks() = runBlocking {
        val pages = FakePages(recognized = mutableSetOf(1))

        val added = pages.addMissingRecognizedText(mapOf(0 to words("bir"), 1 to words("iki"), 2 to words("üç")))

        assertEquals(setOf(0, 2), added)
        assertEquals(mapOf(0 to words("bir"), 2 to words("üç")), pages.added)
    }

    @Test
    fun pagesWithoutWordsAreSkipped() = runBlocking {
        val pages = FakePages(recognized = mutableSetOf())

        assertEquals(emptySet<Int>(), pages.addMissingRecognizedText(mapOf(3 to emptyList())))
        assertEquals(emptyMap<Int, List<OcrWord>>(), pages.added)
    }
}
