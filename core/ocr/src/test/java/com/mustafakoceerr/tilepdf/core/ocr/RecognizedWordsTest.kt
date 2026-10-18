package com.mustafakoceerr.tilepdf.core.ocr

import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.model.OcrWord
import org.junit.Assert.assertEquals
import org.junit.Test

class RecognizedWordsTest {

    private val page = PageSize(600, 800)

    /** A word's corners in reading order, as ML Kit gives them, in bitmap pixels. */
    private fun word(text: String, vararg corners: Pair<Float, Float>) = PixelWord(text, corners.map { (x, y) -> PdfPoint(x, y) })

    @Test
    fun unturnedPageDividesByScale() {
        val words = recognizedWords(
            listOf(word("Merhaba", 20f to 40f, 220f to 40f, 220f to 80f, 20f to 80f)),
            scale = 2f,
            view = PageViewTransform.of(page),
        )

        assertEquals(
            listOf(OcrWord("Merhaba", listOf(PdfPoint(10f, 20f), PdfPoint(110f, 20f), PdfPoint(110f, 40f), PdfPoint(10f, 40f)))),
            words,
        )
    }

    @Test
    fun quarterTurnMapsBackToThePage() {
        for (rotation in listOf(90, 180, 270)) {
            val view = PageViewTransform.of(page, rotation)
            // A word upright on the page, as the turned bitmap shows it.
            val onPage = listOf(PdfPoint(100f, 200f), PdfPoint(300f, 200f), PdfPoint(300f, 230f), PdfPoint(100f, 230f))
            val pixels = onPage.map { view.toView(it) }.map { it.x * 3f to it.y * 3f }

            val words = recognizedWords(listOf(word("yazı", *pixels.toTypedArray())), scale = 3f, view = view)

            assertEquals("rotation $rotation", listOf(OcrWord("yazı", onPage)), words)
        }
    }

    @Test
    fun blankWordsAreDropped() {
        val corners = arrayOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)

        assertEquals(emptyList<OcrWord>(), recognizedWords(listOf(word(" ", *corners), word("", *corners)), 1f, PageViewTransform.of(page)))
    }

    @Test
    fun wordsWithoutFourCornersAreDropped() {
        assertEquals(emptyList<OcrWord>(), recognizedWords(listOf(word("a", 0f to 0f)), 1f, PageViewTransform.of(page)))
    }
}
