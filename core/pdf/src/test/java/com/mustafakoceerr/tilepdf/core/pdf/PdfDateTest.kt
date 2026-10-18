package com.mustafakoceerr.tilepdf.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime

class PdfDateTest {

    private fun epoch(text: String) = OffsetDateTime.parse(text).toInstant().toEpochMilli()

    @Test
    fun fullDateWithOffset() {
        assertEquals(epoch("2026-10-19T12:30:45+03:00"), pdfDate("D:20261019123045+03'00'"))
    }

    @Test
    fun utcAndNoPrefix() {
        assertEquals(epoch("2026-10-19T12:30:45Z"), pdfDate("20261019123045Z"))
    }

    @Test
    fun missingPartsDefaultToTheStartAndUtc() {
        assertEquals(epoch("2026-10-01T00:00:00Z"), pdfDate("D:202610"))
    }

    @Test
    fun negativeOffsetWithoutTrailingQuote() {
        assertEquals(epoch("2026-10-19T08:00:00-05:30"), pdfDate("D:20261019080000-05'30"))
    }

    @Test
    fun garbageIsNull() {
        assertNull(pdfDate(""))
        assertNull(pdfDate("D:yesterday"))
        assertNull(pdfDate("D:20261399"))
    }
}
