package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap

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

    /**
     * Renders page [index] into an ARGB_8888 [bitmap] without clearing it.
     * [matrix] is PDFium's `{a, b, c, d, e, f}` from top-left page points to
     * bitmap pixels. Returns false if the page or bitmap couldn't be used.
     */
    @JvmStatic
    external fun nativeRender(document: Long, index: Int, bitmap: Bitmap, matrix: FloatArray): Boolean

    /** PDFium's `FPDF_ERR_PASSWORD`. */
    const val ERROR_PASSWORD = 4
}
