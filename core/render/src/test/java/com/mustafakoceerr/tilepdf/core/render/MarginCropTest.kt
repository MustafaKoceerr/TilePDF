package com.mustafakoceerr.tilepdf.core.render

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarginCropTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    /** A [width]×[height] white image with [ink] pixels set to [color]. */
    private fun image(width: Int, height: Int, color: Int = black, vararg ink: Pair<Int, Int>) =
        IntArray(width * height) { white }.also { pixels -> ink.forEach { (x, y) -> pixels[y * width + x] = color } }

    @Test
    fun boundsHoldEveryInkedPixel() {
        val pixels = image(10, 8, black, 2 to 3, 6 to 1, 4 to 5)

        assertEquals(PixelRect(2, 1, 7, 6), inkBounds(pixels, 10, 8))
    }

    @Test
    fun blankImageHasNoBounds() = assertNull(inkBounds(image(10, 8), 10, 8))

    @Test
    fun nearWhiteIsPaper() = assertNull(inkBounds(image(10, 8, 0xFFF4F4F4.toInt(), 3 to 3), 10, 8))

    @Test
    fun lightGreyIsInk() = assertEquals(PixelRect(3, 3, 4, 4), inkBounds(image(10, 8, 0xFFC8C8C8.toInt(), 3 to 3), 10, 8))

    private val page = PageSize(600, 800)

    @Test
    fun inkIsScaledToPointsWithPadding() {
        // A 300 px wide sample of a 600 pt page: 2 pt per pixel.
        val crop = marginCrop(PixelRect(50, 60, 250, 340), sampleWidth = 300, page = page, paddingPt = 10f)

        assertEquals(PdfRect(90f, 110f, 510f, 690f), crop)
    }

    @Test
    fun paddingStaysOnThePage() {
        val crop = marginCrop(PixelRect(2, 1, 299, 300), sampleWidth = 300, page = page, paddingPt = 10f)

        assertEquals(PdfRect(0f, 0f, 600f, 610f), crop)
    }

    @Test
    fun blankPageIsNotCropped() = assertNull(marginCrop(null, sampleWidth = 300, page = page, paddingPt = 10f))

    @Test
    fun almostFullPageIsNotCropped() =
        assertNull(marginCrop(PixelRect(3, 3, 297, 397), sampleWidth = 300, page = page, paddingPt = 4f))
}
