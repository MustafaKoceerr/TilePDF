package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Android's own PdfRenderer, kept only as a reference to check PDFium
 * against: same page sizes, same pixels within anti-aliasing tolerance.
 */
internal class PlatformReference(uri: Uri) : AutoCloseable {

    private val fileDescriptor = InstrumentationRegistry.getInstrumentation().targetContext
        .contentResolver.openFileDescriptor(uri, "r")!!
    private val renderer = PdfRenderer(fileDescriptor)

    val pageSizes: List<PageSize> =
        List(renderer.pageCount) { index -> renderer.openPage(index).use { PageSize(it.width, it.height) } }

    fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) {
        renderer.openPage(pageIndex).use { it.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
    }

    override fun close() {
        renderer.close()
        fileDescriptor.close()
    }
}
