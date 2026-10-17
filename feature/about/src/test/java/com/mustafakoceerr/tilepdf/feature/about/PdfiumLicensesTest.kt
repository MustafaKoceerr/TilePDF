package com.mustafakoceerr.tilepdf.feature.about

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfiumLicensesTest {

    @Test
    fun namesComeFromFiles() {
        assertEquals("libjpeg-turbo", componentName("libjpeg_turbo.md"))
        assertEquals("FreeType", componentName("freetype.txt"))
        assertEquals("pdfium-binaries", componentName("pdfium-binaries.txt"))
        assertEquals("Little CMS", componentName("lcms.txt"))
    }

    @Test
    fun ijgLicenseIsTold() = assertEquals("libjpeg-turbo (IJG)", componentName("libjpeg_turbo.ijg"))

    @Test
    fun unknownFileKeepsItsName() = assertEquals("newlib", componentName("newlib.txt"))

    @Test
    fun pdfiumComesFirst() =
        assertEquals("pdfium.txt", pdfiumLicenseFiles(listOf("zlib.txt", "pdfium.txt", "abseil.txt")).first())

    @Test
    fun pdfiumLicensesComeFromTheArchive() = assertEquals(
        listOf("pdfium.txt", "abseil.txt", "libjpeg_turbo.ijg", "zlib.txt"),
        pdfiumLicenseFiles(listOf("zlib.txt", ".DS_Store", "README", "libjpeg_turbo.ijg", "abseil.txt", "pdfium.txt")),
    )
}
