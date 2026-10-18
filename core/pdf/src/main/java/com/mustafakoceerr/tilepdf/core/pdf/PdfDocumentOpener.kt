package com.mustafakoceerr.tilepdf.core.pdf

import android.content.Context
import android.net.Uri
import com.mustafakoceerr.tilepdf.di.PdfDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfDocumentOpener @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:PdfDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * @throws java.io.FileNotFoundException if [uri] can't be opened.
     * @throws SecurityException if the PDF is password protected or access was revoked.
     * @throws java.io.IOException if the file is not a valid PDF.
     */
    suspend fun open(uri: Uri): PdfDocument = withContext(dispatcher) {
        val (fileDescriptor, renderer) = context.openPdfRenderer(uri)
        try {
            val pageSizes = List(renderer.pageCount) { index ->
                renderer.openPage(index).use { PageSize(it.width, it.height) }
            }
            PdfDocument(fileDescriptor, renderer, pageSizes, dispatcher)
        } catch (e: Exception) {
            // A page that fails to open must not leak the renderer either.
            renderer.close()
            fileDescriptor.close()
            throw e
        }
    }
}
