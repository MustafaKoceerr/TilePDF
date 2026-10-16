package com.mustafakoceerr.tilepdf.core.pdf

/**
 * Raw entry points of libtilepdf_pdfium. Callers must run on the PDF
 * dispatcher: PDFium keeps global state and is not thread-safe.
 */
internal object PdfiumNative {

    init {
        System.loadLibrary("tilepdf_pdfium")
    }

    /** Initializes PDFium once per process; later calls do nothing. */
    @JvmStatic
    external fun nativeInit()
}
