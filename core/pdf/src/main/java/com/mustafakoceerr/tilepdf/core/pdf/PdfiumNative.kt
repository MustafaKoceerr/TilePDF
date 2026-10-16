package com.mustafakoceerr.tilepdf.core.pdf

/**
 * Raw entry points of libtilepdf_pdfium. Callers must run on the PDF
 * dispatcher: PDFium keeps global state and is not thread-safe. Document
 * handles are only valid until [nativeCloseDocument].
 */
internal object PdfiumNative {

    init {
        System.loadLibrary("tilepdf_pdfium")
    }

    /** Initializes PDFium once per process; later calls do nothing. */
    @JvmStatic
    external fun nativeInit()

    /**
     * Opens the PDF readable through [fd] (which must stay open until the
     * document is closed). Returns a handle > 0, or minus PDFium's error code.
     */
    @JvmStatic
    external fun nativeOpen(fd: Int, size: Long, password: String?): Long

    @JvmStatic
    external fun nativeCloseDocument(document: Long)

    @JvmStatic
    external fun nativePageCount(document: Long): Int

    /** `[width, height]` in points with page rotation applied; empty on failure. */
    @JvmStatic
    external fun nativePageSize(document: Long, index: Int): FloatArray

    /** PDFium's `FPDF_ERR_PASSWORD`. */
    const val ERROR_PASSWORD = 4
}
