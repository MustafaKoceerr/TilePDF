package com.mustafakoceerr.tilepdf.data.document

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedFileNameTest {

    @Test
    fun pdfNameIsKept() = assertEquals("Rapor.pdf", sharedFileName("Rapor.pdf"))

    @Test
    fun missingExtensionIsAdded() = assertEquals("Rapor.pdf", sharedFileName("Rapor"))

    @Test
    fun upperCaseExtensionIsKept() = assertEquals("SCAN.PDF", sharedFileName("SCAN.PDF"))

    @Test
    fun turkishLettersAreKept() = assertEquals("Ödev çğışü.pdf", sharedFileName("Ödev çğışü.pdf"))

    @Test
    fun reservedCharactersAreReplaced() = assertEquals("a_b_c_d.pdf", sharedFileName("a/b:c*d.pdf"))

    @Test
    fun blankNameFallsBack() {
        assertEquals("document.pdf", sharedFileName(null))
        assertEquals("document.pdf", sharedFileName("  "))
        assertEquals("document.pdf", sharedFileName(".pdf"))
    }

    @Test
    fun longNameIsShortened() {
        val name = sharedFileName("x".repeat(300) + ".pdf")
        assertEquals(MAX_SHARED_NAME, name.length)
        assertEquals(true, name.endsWith(".pdf"))
    }
}
