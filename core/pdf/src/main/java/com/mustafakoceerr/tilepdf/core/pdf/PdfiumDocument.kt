package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
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
 * An open PDF backed by PDFium. Every call runs on the shared PDF dispatcher
 * under a [Mutex], so closing waits for in-flight work and nothing touches
 * the native document afterwards.
 */
class PdfiumDocument internal constructor(
    private val fileDescriptor: ParcelFileDescriptor,
    private val handle: Long,
    /** Size of every page, read once when the document is opened. */
    val pageSizes: List<PageSize>,
    private val dispatcher: CoroutineDispatcher,
) : Closeable {

    private val mutex = Mutex()
    private var closed = false

    val pageCount: Int get() = pageSizes.size

    /**
     * Renders [pageIndex] into [bitmap]. [transform] maps top-left page points
     * to bitmap pixels; the caller is responsible for clearing the bitmap.
     */
    suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) {
        require(bitmap.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888, was ${bitmap.config}" }
        val values = FloatArray(9).also(transform::getValues)
        // Android's Matrix is row-major 3x3; PDFium's is {a, b, c, d, e, f}
        // with x' = a*x + c*y + e and y' = b*x + d*y + f.
        val matrix = floatArrayOf(
            values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y],
            values[Matrix.MSKEW_X], values[Matrix.MSCALE_Y],
            values[Matrix.MTRANS_X], values[Matrix.MTRANS_Y],
        )
        val rendered = withDocument(pageIndex) { PdfiumNative.nativeRender(it, pageIndex, bitmap, matrix) }
        if (!rendered) throw PdfCorruptException("Page $pageIndex could not be rendered")
    }

    /** Runs [block] with the native handle, after checking [pageIndex] if given. */
    internal suspend fun <T> withDocument(pageIndex: Int? = null, block: (Long) -> T): T {
        if (pageIndex != null) {
            require(pageIndex in 0 until pageCount) { "Page $pageIndex out of 0 until $pageCount" }
        }
        return withContext(dispatcher) {
            mutex.withLock {
                check(!closed) { "Document is closed" }
                block(handle)
            }
        }
    }

    /**
     * Closes the document once any in-flight call finishes. Safe to call from
     * non-suspending contexts such as `ViewModel.onCleared`.
     */
    override fun close() {
        CoroutineScope(dispatcher + NonCancellable).launch {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                PdfiumNative.nativeCloseDocument(handle)
                fileDescriptor.close()
            }
        }
    }
}
