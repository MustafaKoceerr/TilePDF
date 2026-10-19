package com.mustafakoceerr.tilepdf.core.pdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfThreadTest {

    @Test
    fun everyCallRunsOnTheSameThread() = runBlocking {
        val threads = List(20) {
            async(Dispatchers.Default) { withContext(PdfThread.dispatcher) { Thread.currentThread() } }
        }.awaitAll().toSet()

        assertEquals(1, threads.size)
        assertEquals("pdfium", threads.single().name)
    }
}
