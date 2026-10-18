package com.mustafakoceerr.tilepdf.model

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignatureTest {

    @Test
    fun strokesAreScaledToUnitWidth() {
        val signature = normalizeSignature(listOf(listOf(PdfPoint(100f, 50f), PdfPoint(300f, 150f)), listOf(PdfPoint(200f, 100f))))!!

        assertEquals(listOf(listOf(PdfPoint(0f, 0f), PdfPoint(1f, 0.5f)), listOf(PdfPoint(0.5f, 0.25f))), signature.strokes)
        assertEquals(0.5f, signature.height, 0.0001f)
    }

    @Test
    fun verticalLineKeepsItsShape() {
        val signature = normalizeSignature(listOf(listOf(PdfPoint(10f, 0f), PdfPoint(10f, 40f))))!!

        assertEquals(listOf(listOf(PdfPoint(0f, 0f), PdfPoint(0f, 1f))), signature.strokes)
        assertEquals(1f, signature.height, 0.0001f)
    }

    @Test
    fun nothingDrawnIsNoSignature() {
        assertNull(normalizeSignature(emptyList()))
        assertNull(normalizeSignature(listOf(emptyList())))
    }

    @Test
    fun placedSignatureIsCentredOnTheTap() {
        val signature = Signature(listOf(listOf(PdfPoint(0f, 0f), PdfPoint(1f, 0.5f))), height = 0.5f)

        val placed = signature.placedAround(PdfPoint(300f, 400f), widthPt = 160f)

        assertEquals(listOf(listOf(PdfPoint(220f, 360f), PdfPoint(380f, 440f))), placed)
    }
}
