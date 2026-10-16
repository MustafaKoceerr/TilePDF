package com.mustafakoceerr.tilepdf.data.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfEngine
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import javax.inject.Inject

class SafDocumentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val engine: PdfEngine,
) : DocumentRepository {

    private val contentResolver get() = context.contentResolver

    /** Private copies of documents that couldn't be reopened from their source. */
    private val copiesDir get() = File(context.filesDir, "documents")

    override fun persistAccess(uri: String) {
        // Some providers don't offer persistable grants; the one-off grant
        // from the picker still works for the current session.
        takePersistableGrant(uri.toUri())
    }

    override suspend fun accessibleUris(): Set<String> = withContext(ioDispatcher) {
        val granted = contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri.toString() }
        copiesDir.listFiles { file -> file.extension == "pdf" }.orEmpty()
            .mapTo(granted) { Uri.fromFile(it).toString() }
    }

    override suspend fun displayName(uri: String): String? = withContext(ioDispatcher) {
        val parsed = uri.toUri()
        if (parsed.scheme == "file") return@withContext parsed.lastPathSegment
        contentResolver.query(parsed, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    override suspend fun prepare(uri: String): String = withContext(ioDispatcher) {
        val parsed = uri.toUri()
        if (isPrivateCopy(parsed)) return@withContext uri
        // One descriptor: pipe-backed providers produce the stream once.
        val descriptor = contentResolver.openFileDescriptor(parsed, "r")
            ?: throw FileNotFoundException("Cannot open $uri")
        // PDFium needs the file's length, and a grant to reopen it later.
        if (descriptor.statSize > 0 && holdsOrTakesGrant(parsed)) {
            descriptor.close()
            uri
        } else {
            copy(parsed, ParcelFileDescriptor.AutoCloseInputStream(descriptor))
        }
    }

    override suspend fun forget(uri: String) = withContext(ioDispatcher) {
        val parsed = uri.toUri()
        if (isPrivateCopy(parsed)) File(parsed.path!!).delete()
        Unit
    }

    override suspend fun open(uri: String, password: String?): PdfDocument = engine.open(uri.toUri(), password)

    private fun isPrivateCopy(uri: Uri): Boolean {
        val path = uri.path ?: return false
        return uri.scheme == "file" && File(path).parentFile?.canonicalPath == copiesDir.canonicalPath
    }

    private fun holdsOrTakesGrant(uri: Uri): Boolean =
        contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } ||
            takePersistableGrant(uri)

    private fun takePersistableGrant(uri: Uri): Boolean = try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (_: SecurityException) {
        false
    }

    /**
     * Streams [uri] into a private file, written under a temporary name and
     * renamed only once complete, so a failed copy never looks like a PDF.
     */
    private fun copy(uri: Uri, input: InputStream): String {
        copiesDir.mkdirs()
        val target = File(copiesDir, copyName(uri))
        val partial = File(copiesDir, target.name + ".part")
        try {
            input.use { source -> partial.outputStream().use { source.copyTo(it) } }
            if (!partial.renameTo(target)) {
                target.delete()
                if (!partial.renameTo(target)) throw IOException("Cannot store a copy of $uri")
            }
        } finally {
            partial.delete()
        }
        return Uri.fromFile(target).toString()
    }

    /** `<display name>-<8 hex of the source URI's SHA-256>.pdf`, safe as a file name. */
    private fun copyName(uri: Uri): String {
        val displayName = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        val base = (displayName ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
            .replace(Regex("[^A-Za-z0-9._-]"), "_").take(60).ifEmpty { "document" }
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray())
            .take(4).joinToString("") { "%02x".format(it) }
        return "$base-$hash.pdf"
    }
}
