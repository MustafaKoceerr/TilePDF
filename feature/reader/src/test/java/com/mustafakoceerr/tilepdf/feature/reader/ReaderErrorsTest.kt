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
    fun passwordProtectedAsksForThePassword() {
        // PdfPasswordException is an IOException; it must not fall through to Corrupt.
        assertEquals(OpenFailure.NeedsPassword(wrongPassword = false), openFailureFor(PdfPasswordException(false)))
        assertEquals(OpenFailure.NeedsPassword(wrongPassword = true), openFailureFor(PdfPasswordException(true)))
    }

    @Test
    fun missingFileIsNotFound() {
        assertEquals(OpenFailure.Failed(ReaderError.NotFound), openFailureFor(FileNotFoundException()))
    }

    @Test
    fun revokedPermissionIsNotFound() {
        assertEquals(OpenFailure.Failed(ReaderError.NotFound), openFailureFor(SecurityException("grant revoked")))
    }

    @Test
    fun unreadablePdfIsCorrupt() {
        assertEquals(OpenFailure.Failed(ReaderError.Corrupt), openFailureFor(PdfCorruptException("bad xref")))
        assertEquals(OpenFailure.Failed(ReaderError.Corrupt), openFailureFor(IOException("read failed")))
    }

    @Test
    fun programmingErrorsAreNotMapped() {
        assertNull(openFailureFor(IllegalStateException()))
    }
}
