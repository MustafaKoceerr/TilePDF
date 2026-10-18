package com.mustafakoceerr.tilepdf.core.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfiumNativeTest {

    @Test
    fun initializesLibrary() {
        // Loads libtilepdf_pdfium.so and libpdfium.so for this ABI; a second
        // call must be a no-op rather than re-initializing PDFium.
        // On the PDF thread, as the app does: the script engine stays bound to it.
        runBlocking(PdfThread.dispatcher) {
            PdfiumNative.nativeInit(null)
            PdfiumNative.nativeInit(null)
        }
    }
}
