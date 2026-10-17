package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.feature.reader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AnnotationColorNamesTest {

    @Test
    fun paletteColorsHaveDistinctNames() {
        val names = AnnotationPalette.map(::colorNameRes)
        assertEquals(AnnotationPalette.size, names.distinct().size)
        assertFalse(R.string.annotation_color in names)
    }

    @Test
    fun colorOutsidePaletteIsJustColor() {
        assertEquals(R.string.annotation_color, colorNameRes(0xFF123456.toInt()))
    }
}
