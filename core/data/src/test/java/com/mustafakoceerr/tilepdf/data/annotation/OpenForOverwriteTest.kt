package com.mustafakoceerr.tilepdf.data.annotation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

class OpenForOverwriteTest {

    private val stream = ByteArrayOutputStream()

    @Test
    fun wtUsedWhenSupported() {
        val modes = mutableListOf<String>()
        assertSame(stream, openForOverwrite { modes += it; stream })
        assertEquals(listOf("wt"), modes)
    }

    @Test
    fun wtRejectedFallsBackToW() {
        val modes = mutableListOf<String>()
        val result = openForOverwrite { mode ->
            modes += mode
            if (mode == "wt") throw IllegalArgumentException("Bad mode: wt") else stream
        }
        assertSame(stream, result)
        assertEquals(listOf("wt", "w"), modes)
    }

    @Test(expected = SecurityException::class)
    fun securityExceptionIsNotSwallowed() {
        openForOverwrite { throw SecurityException("read only") }
    }

    @Test(expected = FileNotFoundException::class)
    fun noStreamFromEitherModeThrows() {
        openForOverwrite { null }
    }
}
