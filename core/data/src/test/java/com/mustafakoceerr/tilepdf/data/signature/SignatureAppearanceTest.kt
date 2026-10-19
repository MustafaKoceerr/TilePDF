package com.mustafakoceerr.tilepdf.data.signature

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.Signature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureAppearanceTest {

    private val box = PdfRect(100f, 200f, 280f, 260f)
    private val drawn = Signature(listOf(listOf(PdfPoint(0f, 0f), PdfPoint(1f, 0.4f))), height = 0.4f)

    private fun inside(x: Float, y: Float) = x >= box.left && x <= box.right && y >= box.top && y <= box.bottom

    @Test
    fun withoutADrawingTheLinesFillTheBox() {
        val appearance = signatureAppearance(box, listOf("Ada Lovelace", "19.10.2026 14:05", "Dijital olarak imzalandı"), drawing = null)

        assertEquals(listOf("Ada Lovelace", "19.10.2026 14:05", "Dijital olarak imzalandı"), appearance.lines.map { it.text })
        assertTrue(appearance.strokes.isEmpty())
        val baselines = appearance.lines.map { it.baseline }
        assertEquals(baselines.sorted(), baselines)
        appearance.lines.forEach { line ->
            assertTrue("$line", inside(line.x, line.baseline) && inside(line.x, line.baseline - line.size))
            assertTrue("fits across: $line", line.x + line.size * 0.6f * line.text.length <= box.right + 0.5f)
        }
    }

    @Test
    fun aDrawingTakesTheLeftAndTheLinesTheRight() {
        val appearance = signatureAppearance(box, listOf("Ada Lovelace", "19.10.2026"), drawing = drawn)

        val points = appearance.strokes.flatten()
        assertTrue(points.isNotEmpty())
        val split = box.left + box.width * 0.5f
        assertTrue("drawing on the left: $points", points.all { inside(it.x, it.y) && it.x <= split + 0.5f })
        assertTrue(appearance.lines.all { it.x >= split })
    }

    @Test
    fun longNamesShrinkToFit() {
        val name = "Prof. Dr. Ayşegül Karaosmanoğlu-Büyükşahin"
        val appearance = signatureAppearance(box, listOf(name), drawing = null)

        val line = appearance.lines.single()
        assertTrue("$line", line.x + line.size * 0.6f * name.length <= box.right + 0.5f)
        assertTrue(line.size >= 4f)
    }

    @Test
    fun unusualSpacesBecomePlainOnes() {
        // Android writes "7:12\u202FAM"; the font has no narrow no-break space.
        val appearance = signatureAppearance(box, listOf("Oct 19, 2026, 7:12\u202FAM", "A\u00A0B"), drawing = null)

        assertEquals(listOf("Oct 19, 2026, 7:12 AM", "A B"), appearance.lines.map { it.text })
    }
}
