package com.mustafakoceerr.tilepdf.core.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenedHandleTest {

    @Test
    fun taggedArm64PointerIsASuccess() {
        // Android 11+ tags heap pointers in the top byte on arm64, so a valid
        // handle is negative when read as a Long.
        val tagged = 0xB400007B12345678uL.toLong()
        assertEquals(tagged, openedHandle(tagged, error = 0, password = null))
    }

    @Test
    fun zeroWithPasswordErrorAsksForPassword() {
        val missing = runCatching { openedHandle(0L, PdfiumNative.ERROR_PASSWORD, password = null) }.exceptionOrNull()
        assertFalse((missing as PdfPasswordException).wrongPassword)
        val wrong = runCatching { openedHandle(0L, PdfiumNative.ERROR_PASSWORD, password = "x") }.exceptionOrNull()
        assertTrue((wrong as PdfPasswordException).wrongPassword)
    }

    @Test
    fun zeroWithOtherErrorIsCorrupt() {
        val error = runCatching { openedHandle(0L, error = 3, password = null) }.exceptionOrNull()
        assertTrue(error is PdfCorruptException)
    }
}
