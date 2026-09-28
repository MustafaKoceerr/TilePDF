package com.mustafakoceerr.tilepdf.data.pdfbox

import android.content.Context
import androidx.core.net.toUri
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** Opens SAF documents with PdfBox; shared by everything that reads PDFs through it. */
class PdfBoxDocumentLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** The caller owns the result and must close it. */
    suspend fun load(uri: String): PDDocument = withContext(ioDispatcher) {
        PDFBoxResourceLoader.init(context)
        context.contentResolver.openInputStream(uri.toUri())?.use {
            // Buffer the file on disk rather than the heap: PDFs can be large.
            PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly())
        } ?: throw FileNotFoundException("Cannot open $uri")
    }
}
