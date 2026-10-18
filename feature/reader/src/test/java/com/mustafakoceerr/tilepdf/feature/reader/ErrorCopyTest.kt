package com.mustafakoceerr.tilepdf.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ErrorCopyTest {

    @Test
    fun lostFilesOfferChoosingAgain() {
        listOf(ReaderError.NotFound, ReaderError.PermissionLost).forEach {
            assertEquals(ErrorAction.ChooseFile, it.errorCopy().primary)
            assertEquals(ErrorAction.Back, it.errorCopy().secondary)
        }
    }

    @Test
    fun corruptOnlyGoesBack() {
        assertEquals(ErrorAction.Back, ReaderError.Corrupt.errorCopy().primary)
        assertNull(ReaderError.Corrupt.errorCopy().secondary)
    }

    @Test
    fun memoryErrorRetries() {
        assertEquals(ErrorAction.Retry, ReaderError.OutOfMemory.errorCopy().primary)
        assertEquals(ErrorAction.Back, ReaderError.OutOfMemory.errorCopy().secondary)
    }

    @Test
    fun eachErrorHasItsOwnText() {
        assertEquals(ReaderError.entries.size, ReaderError.entries.map { it.errorCopy().title }.distinct().size)
        assertEquals(ReaderError.entries.size, ReaderError.entries.map { it.errorCopy().message }.distinct().size)
    }
}
