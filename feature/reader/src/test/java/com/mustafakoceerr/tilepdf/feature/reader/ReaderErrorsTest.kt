package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PdfCorruptException
import com.mustafakoceerr.tilepdf.core.pdf.PdfPasswordException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

class ReaderErrorsTest {

    @Test
    fun passwordProtectedIsNotReportedAsCorrupt() {
        // PdfPasswordException is an IOException; it must not fall through to Corrupt.
        assertEquals(ReaderError.PasswordProtected, readerErrorFor(PdfPasswordException(wrongPassword = false)))
        assertEquals(ReaderError.PasswordProtected, readerErrorFor(PdfPasswordException(wrongPassword = true)))
    }

    @Test
    fun missingFileIsNotFound() {
        assertEquals(ReaderError.NotFound, readerErrorFor(FileNotFoundException()))
    }

    @Test
    fun revokedPermissionIsNotFound() {
        assertEquals(ReaderError.NotFound, readerErrorFor(SecurityException("grant revoked")))
    }

    @Test
    fun unreadablePdfIsCorrupt() {
        assertEquals(ReaderError.Corrupt, readerErrorFor(PdfCorruptException("bad xref")))
        assertEquals(ReaderError.Corrupt, readerErrorFor(IOException("read failed")))
    }

    @Test
    fun programmingErrorsAreNotMapped() {
        assertNull(readerErrorFor(IllegalStateException()))
    }
}
