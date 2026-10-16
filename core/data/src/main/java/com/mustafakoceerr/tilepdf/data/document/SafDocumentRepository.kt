package com.mustafakoceerr.tilepdf.data.document

import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfEngine
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class SafDocumentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val engine: PdfEngine,
) : DocumentRepository {

    private val contentResolver get() = context.contentResolver

    override fun persistAccess(uri: String) {
        // Some providers don't offer persistable grants; the one-off grant
        // from the picker still works for the current session.
        try {
            contentResolver.takePersistableUriPermission(
                uri.toUri(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
    }

    override suspend fun accessibleUris(): Set<String> = withContext(ioDispatcher) {
        contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(HashSet()) { it.uri.toString() }
    }

    override suspend fun displayName(uri: String): String? = withContext(ioDispatcher) {
        contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }

    override suspend fun open(uri: String, password: String?): PdfDocument = engine.open(uri.toUri(), password)
}
