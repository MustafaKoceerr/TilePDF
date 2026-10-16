package com.mustafakoceerr.tilepdf.core.pdf

import android.content.Context
import android.net.Uri
import com.mustafakoceerr.tilepdf.di.PdfDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
) : PdfEngine {
    override suspend fun open(uri: Uri, password: String?): PdfiumDocument {
        // Not cancellable midway: a cancelled withContext would drop a document
        // it had already opened. Cancellation is honoured right after instead.
        val document = withContext(dispatcher + NonCancellable) { openNow(uri, password) }
        try {
            currentCoroutineContext().ensureActive()
        } catch (e: CancellationException) {
            document.close()
            throw e
        }
        return document
    }

    private fun openNow(uri: Uri, password: String?): PdfiumDocument {
        PdfiumNative.nativeInit()
        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Cannot open $uri")
        try {
            val size = fileDescriptor.statSize
            if (size <= 0) throw PdfCorruptException("Empty or unsized file")
            val error = IntArray(1)
            val handle = openedHandle(PdfiumNative.nativeOpen(fileDescriptor.fd, size, password, error), error[0], password)
            val pageSizes = try {
                List(PdfiumNative.nativePageCount(handle)) { index -> pageSize(handle, index) }
            } catch (e: Exception) {
                PdfiumNative.nativeCloseDocument(handle)
                throw e
            }
            return PdfiumDocument(fileDescriptor, handle, pageSizes, dispatcher)
        } catch (e: Exception) {
            fileDescriptor.close()
            throw e
        }
    }

    private fun pageSize(handle: Long, index: Int): PageSize {
        val size = PdfiumNative.nativePageSize(handle, index)
        if (size.size != 2) throw PdfCorruptException("Page $index has no size")
        // Truncated to whole points, as PdfRenderer did, so saved layouts and
        // reading positions stay the same after the switch.
        return PageSize(size[0].toInt(), size[1].toInt())
    }
}

/**
 * Checks the result of [PdfiumNative.nativeOpen]. Only 0 means failure: on
 * arm64, Android 11+ tags heap pointers in the top byte, so a valid handle
 * is often negative.
 */
internal fun openedHandle(handle: Long, error: Int, password: String?): Long {
    if (handle == 0L) {
        throw when (error) {
            PdfiumNative.ERROR_PASSWORD -> PdfPasswordException(wrongPassword = password != null)
            else -> PdfCorruptException("PDFium error $error")
        }
    }
    return handle
}
