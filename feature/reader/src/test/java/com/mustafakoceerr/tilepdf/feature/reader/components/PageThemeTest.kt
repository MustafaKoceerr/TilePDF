package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.graphics.ColorMatrix
import com.mustafakoceerr.tilepdf.model.PageTheme
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageThemeTest {

    /** Applies a 4x5 colour matrix to an opaque RGB colour, as Android does. */
    private fun ColorMatrix.apply(r: Int, g: Int, b: Int): FloatArray {
        val m = values
        return FloatArray(3) { row ->
            (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 3] * 255 + m[row * 5 + 4])
                .coerceIn(0f, 255f)
        }
    }

    @Test
    fun lightHasNoFilter() {
        assertNull(PageTheme.Light.colorMatrix())
    }

    @Test
    fun nightTurnsWhiteBlack() {
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), PageTheme.Night.colorMatrix()!!.apply(255, 255, 255), 0.5f)
    }

    @Test
    fun sepiaTurnsWhitePaper() {
        assertArrayEquals(floatArrayOf(244f, 236f, 216f), PageTheme.Sepia.colorMatrix()!!.apply(255, 255, 255), 1f)
    }

    @Test
    fun sepiaKeepsBlackBlack() {
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), PageTheme.Sepia.colorMatrix()!!.apply(0, 0, 0), 0.5f)
    }

    @Test
    fun greenTurnsWhitePaleGreenAndKeepsBlackBlack() {
        val green = PageTheme.Green.colorMatrix()!!
        assertArrayEquals(floatArrayOf(204f, 232f, 207f), green.apply(255, 255, 255), 1f)
        assertArrayEquals(floatArrayOf(0f, 0f, 0f), green.apply(0, 0, 0), 0.5f)
    }

    @Test
    fun dimTurnsWhiteDarkGreyAndBlackLightGrey() {
        // Softer than night: no pure white text on pure black.
        val dim = PageTheme.Dim.colorMatrix()!!
        assertArrayEquals(floatArrayOf(43f, 43f, 43f), dim.apply(255, 255, 255), 1f)
        assertArrayEquals(floatArrayOf(200f, 200f, 200f), dim.apply(0, 0, 0), 1f)
    }

    @Test
    fun warmNightTurnsWhiteDarkBrownAndBlackCream() {
        val warm = PageTheme.WarmNight.colorMatrix()!!
        assertArrayEquals(floatArrayOf(42f, 33f, 24f), warm.apply(255, 255, 255), 1f)
        assertArrayEquals(floatArrayOf(232f, 217f, 192f), warm.apply(0, 0, 0), 1f)
    }

    @Test
    fun thePaperShownBeforeAPageArrivesIsWhatWhiteBecomes() {
        // Otherwise a page would flash another colour as its image lands.
        for (theme in PageTheme.entries) {
            val white = theme.colorMatrix()?.apply(255, 255, 255) ?: floatArrayOf(255f, 255f, 255f)
            val paper = theme.paperColor
            assertArrayEquals(theme.name, white, floatArrayOf(paper.red * 255, paper.green * 255, paper.blue * 255), 1f)
        }
    }

    @Test
    fun lightRecolorIsIdentity() {
        assertEquals(0x80FFEB3B.toInt(), PageTheme.Light.recolor(0x80FFEB3B.toInt()))
    }

    @Test
    fun nightRecolorInvertsAndKeepsAlpha() {
        assertEquals(0x80000000.toInt(), PageTheme.Night.recolor(0x80FFFFFF.toInt()))
        assertEquals(0xFF00FFFF.toInt(), PageTheme.Night.recolor(0xFFFF0000.toInt()))
    }
}
