package com.mustafakoceerr.tilepdf.data.annotation

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.mustafakoceerr.tilepdf.core.pdf.EditablePdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfEngine
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.FileStamp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** [AnnotationFiles] over the Storage Access Framework, `file:` URIs and PDFium. */
class SafAnnotationFiles @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val engine: PdfEngine,
) : AnnotationFiles {

    private val contentResolver get() = context.contentResolver

    private val tempDir get() = File(context.cacheDir, "annotation-saves").apply { mkdirs() }

    override suspend fun stamp(uri: String): FileStamp? = withContext(ioDispatcher) {
        val parsed = uri.toUri()
        if (parsed.scheme == "file") {
            val file = File(parsed.path!!)
            return@withContext if (file.exists()) FileStamp(file.length(), file.lastModified()) else null
        }
        try {
            // All columns: providers that aren't document providers may reject
            // COLUMN_LAST_MODIFIED in a projection.
            contentResolver.query(parsed, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val size = cursor.getColumnIndex(OpenableColumns.SIZE)
                val modified = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                FileStamp(
                    size = if (size >= 0 && !cursor.isNull(size)) cursor.getLong(size) else -1,
                    lastModified = if (modified >= 0 && !cursor.isNull(modified)) cursor.getLong(modified) else null,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: RuntimeException) {
            null
        }
    }

    override suspend fun openForEditing(uri: String, password: String?): EditablePdfDocument =
        engine.openForEditing(uri.toUri(), password)

    override suspend fun openForReading(file: File, password: String?): PdfDocument =
        engine.open(Uri.fromFile(file), password)

    override fun newTempFile(): File = File.createTempFile("save", ".pdf", tempDir)

    override suspend fun replace(uri: String, with: File) {
        withContext(ioDispatcher + NonCancellable) {
            openForOverwrite { mode -> contentResolver.openOutputStream(uri.toUri(), mode) }.use { output ->
                with.inputStream().use { it.copyTo(output) }
                output.flush()
                (output as? FileOutputStream)?.fd?.sync()
            }
        }
    }
}
