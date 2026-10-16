package com.mustafakoceerr.tilepdf.core.pdf

import android.content.Context
import android.net.Uri
import com.mustafakoceerr.tilepdf.di.PdfDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Opens PDFs with PDFium. Every document shares [dispatcher], because PDFium's
 * state is global to the process, not per document.
 */
@Singleton
class Pdfium @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:PdfDispatcher private val dispatcher: CoroutineDispatcher,
) {
    /**
     * @throws FileNotFoundException if [uri] can't be opened.
     * @throws PdfPasswordException if the document needs a (different) password.
     * @throws PdfCorruptException if the file isn't a readable PDF.
     */
    suspend fun open(uri: Uri, password: String? = null): PdfiumDocument = withContext(dispatcher) {
        PdfiumNative.nativeInit()
        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Cannot open $uri")
        try {
            val size = fileDescriptor.statSize
            if (size <= 0) throw PdfCorruptException("Empty or unsized file")
            val handle = PdfiumNative.nativeOpen(fileDescriptor.fd, size, password)
            if (handle <= 0) {
                throw when ((-handle).toInt()) {
                    PdfiumNative.ERROR_PASSWORD -> PdfPasswordException(wrongPassword = password != null)
                    else -> PdfCorruptException("PDFium error ${-handle}")
                }
            }
            val pageSizes = try {
                List(PdfiumNative.nativePageCount(handle)) { index -> pageSize(handle, index) }
            } catch (e: Exception) {
                PdfiumNative.nativeCloseDocument(handle)
                throw e
            }
            PdfiumDocument(fileDescriptor, handle, pageSizes, dispatcher)
        } catch (e: Exception) {
            fileDescriptor.close()
            throw e
        }
    }

    private fun pageSize(handle: Long, index: Int): PageSize {
        val size = PdfiumNative.nativePageSize(handle, index)
        if (size.size != 2) throw PdfCorruptException("Page $index has no size")
        // Truncated like PdfRenderer, so both engines lay pages out identically.
        return PageSize(size[0].toInt(), size[1].toInt())
    }
}
