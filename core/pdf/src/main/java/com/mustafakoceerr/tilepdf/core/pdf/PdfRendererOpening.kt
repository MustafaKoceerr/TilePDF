package com.mustafakoceerr.tilepdf.core.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Opens [uri] with PdfRenderer. The file descriptor must stay open for the
 * renderer's lifetime, so both are returned; on failure it is closed here.
 * Blocking: call from a background dispatcher.
 *
 * @throws FileNotFoundException if [uri] can't be opened.
 * @throws SecurityException if the PDF is password protected.
 * @throws java.io.IOException if the file is not a valid PDF.
 *
 * Public only for NativePdfTextProvider; removed with PdfRenderer in phase 3.
 */
fun Context.openPdfRenderer(uri: Uri): Pair<ParcelFileDescriptor, PdfRenderer> {
    val fileDescriptor = contentResolver.openFileDescriptor(uri, "r")
        ?: throw FileNotFoundException("Cannot open $uri")
    return try {
        fileDescriptor to PdfRenderer(fileDescriptor)
    } catch (e: Exception) {
        fileDescriptor.close()
        throw e
    }
}
