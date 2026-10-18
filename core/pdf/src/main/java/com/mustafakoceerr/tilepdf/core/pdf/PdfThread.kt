package com.mustafakoceerr.tilepdf.core.pdf

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

/**
 * The one thread every PDFium call runs on. PDFium isn't thread-safe, and the
 * V8 engine that runs form scripts is bound to the thread that started it:
 * calls one at a time on changing threads make scripts fail silently, so it
 * has to be the same thread, for the whole process.
 */
object PdfThread {

    /** Room for V8, which keeps about 1 MB of stack for scripts. */
    private const val STACK_BYTES = 2L * 1024 * 1024

    val dispatcher: CoroutineDispatcher = Executors.newSingleThreadExecutor { task ->
        Thread(null, task, "pdfium", STACK_BYTES).apply { isDaemon = true }
    }.asCoroutineDispatcher()
}
