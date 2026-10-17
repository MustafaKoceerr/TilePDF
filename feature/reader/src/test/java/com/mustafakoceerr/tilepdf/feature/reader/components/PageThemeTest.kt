package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.graphics.ColorMatrix
import com.mustafakoceerr.tilepdf.model.PageTheme
import org.junit.Assert.assertArrayEquals
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
}
