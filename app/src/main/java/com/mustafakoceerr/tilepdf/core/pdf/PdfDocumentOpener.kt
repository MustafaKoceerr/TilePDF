package com.mustafakoceerr.tilepdf.core.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.mustafakoceerr.tilepdf.di.PdfDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfDocumentOpener @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:PdfDispatcher private val dispatcher: CoroutineDispatcher,
) {

    /**
     * @throws FileNotFoundException if [uri] can't be opened.
     * @throws SecurityException if the PDF is password protected or access was revoked.
     * @throws java.io.IOException if the file is not a valid PDF.
     */
    suspend fun open(uri: Uri): PdfDocument = withContext(dispatcher) {
        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: throw FileNotFoundException("Cannot open $uri")
        try {
            val renderer = PdfRenderer(fileDescriptor)
            val pageSizes = List(renderer.pageCount) { index ->
                renderer.openPage(index).use { PageSize(it.width, it.height) }
            }
            PdfDocument(fileDescriptor, renderer, pageSizes, dispatcher)
        } catch (e: Exception) {
            fileDescriptor.close()
            throw e
        }
    }
}
