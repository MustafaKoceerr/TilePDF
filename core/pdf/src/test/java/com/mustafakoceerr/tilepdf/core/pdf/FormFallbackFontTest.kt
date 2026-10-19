package com.mustafakoceerr.tilepdf.core.pdf

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

class FormFallbackFontTest {

    @Test
    fun readsTheAsset() {
        val opened = mutableListOf<String>()

        val bytes = FormFallbackFont.read { name ->
            opened += name
            ByteArrayInputStream(byteArrayOf(1, 2, 3))
        }

        assertEquals(listOf(FormFallbackFont.ASSET), opened)
        assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
    }

    @Test
    fun missingFontIsNull() {
        assertNull(FormFallbackFont.read { throw IOException("no such asset") })
    }

    @Test
    fun bundledFontCoversTurkish() {
        val mapped = cmapCodePoints(File("src/main/assets/${FormFallbackFont.ASSET}").readBytes())

        val wanted = "İıŞşĞğÇçÖöÜü₺€".map { it.code } + (0x20..0x7E)
        assertTrue("Missing: ${wanted.filterNot { it in mapped }.map { "U+%04X".format(it) }}", mapped.containsAll(wanted))
    }
}

/** The code points a TrueType font's format 4 `cmap` subtable maps to a glyph. */
private fun cmapCodePoints(font: ByteArray): Set<Int> {
    val data = ByteBuffer.wrap(font)
    val tableCount = data.getShort(4).toInt() and 0xFFFF
    val cmap = (0 until tableCount).map { 12 + it * 16 }
        .first { String(font, it, 4, Charsets.US_ASCII) == "cmap" }
        .let { data.getInt(it + 8) }
    val subtables = data.getShort(cmap + 2).toInt() and 0xFFFF
    val format4 = (0 until subtables).map { cmap + data.getInt(cmap + 4 + it * 8 + 4) }
        .first { data.getShort(it).toInt() == 4 }
    val segments = (data.getShort(format4 + 6).toInt() and 0xFFFF) / 2
    val ends = format4 + 14
    val starts = ends + segments * 2 + 2
    val deltas = starts + segments * 2
    val rangeOffsets = deltas + segments * 2
    val mapped = mutableSetOf<Int>()
    for (i in 0 until segments) {
        val start = data.getShort(starts + i * 2).toInt() and 0xFFFF
        val end = data.getShort(ends + i * 2).toInt() and 0xFFFF
        val delta = data.getShort(deltas + i * 2).toInt()
        val rangeOffset = data.getShort(rangeOffsets + i * 2).toInt() and 0xFFFF
        for (code in start..end) {
            if (code == 0xFFFF) continue
            val glyph = if (rangeOffset == 0) {
                (code + delta) and 0xFFFF
            } else {
                val at = rangeOffsets + i * 2 + rangeOffset + (code - start) * 2
                (data.getShort(at).toInt() and 0xFFFF).let { if (it == 0) 0 else (it + delta) and 0xFFFF }
            }
            if (glyph != 0) mapped += code
        }
    }
    return mapped
}
