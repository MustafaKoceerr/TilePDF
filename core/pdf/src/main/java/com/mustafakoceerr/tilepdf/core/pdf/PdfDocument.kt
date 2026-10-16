package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * An open PDF backed by [PdfRenderer].
 *
 * PdfRenderer is not thread-safe and only one page may be open at a time, so
 * every access runs on a single-threaded [dispatcher] under a [Mutex]. Each
 * page is opened, used, and closed within one call.
 */
class PdfDocument internal constructor(
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Size of every page, read once when the document is opened. */
    val pageSizes: List<PageSize>,
    private val dispatcher: CoroutineDispatcher,
) : Closeable {

    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    /**
     * Renders [pageIndex] into [bitmap]. [transform] maps page points to bitmap
     * pixels; the caller is responsible for clearing the bitmap beforehand.
     */
    suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) =
        withContext(dispatcher) {
            mutex.withLock {
                check(!closed) { "Document is closed" }
                renderer.openPage(pageIndex).use { page ->
                    page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }

    /**
     * Closes the renderer once any in-flight render finishes. Safe to call from
     * non-suspending contexts such as `ViewModel.onCleared`.
     */
    override fun close() {
        CoroutineScope(dispatcher + NonCancellable).launch {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                renderer.close()
                fileDescriptor.close()
            }
        }
    }
}
