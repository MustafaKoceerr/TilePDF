package com.mustafakoceerr.tilepdf.data.document

import android.content.Context
import androidx.core.content.FileProvider
import com.mustafakoceerr.tilepdf.data.annotation.DocumentExporting
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

internal const val MAX_SHARED_NAME = 120

private const val PDF_EXTENSION = ".pdf"
private const val FALLBACK_NAME = "document"
private val RESERVED = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

/** A name for [displayName] that every file system takes, ending in .pdf. */
internal fun sharedFileName(displayName: String?): String {
    val name = displayName.orEmpty().trim()
    val base = (if (name.endsWith(PDF_EXTENSION, ignoreCase = true)) name.dropLast(PDF_EXTENSION.length) else name)
        .replace(RESERVED, "_")
        .trim()
        .ifEmpty { FALLBACK_NAME }
    val extension = if (name.endsWith(PDF_EXTENSION, ignoreCase = true)) name.takeLast(PDF_EXTENSION.length) else PDF_EXTENSION
    return base.take(MAX_SHARED_NAME - extension.length) + extension
}

/**
 * Documents handed to other apps, to share or print. Each is a fresh file
 * with the annotations written in, so the receiver gets what the reader shows
 * even when the original can't be saved to.
 */
class SharedDocuments @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val exporter: DocumentExporting,
) {

    /** Matches the cache-path in the app's file_paths.xml. */
    private val sharedDir get() = File(context.cacheDir, "shared")

    /**
     * Writes [uri] with its annotations into a file other apps can read and
     * returns its content URI, or null when it couldn't be written.
     */
    suspend fun export(uri: String, displayName: String?): String? = withContext(ioDispatcher) {
        // Only the latest is kept: the cache holds no stale copies of documents.
        sharedDir.deleteRecursively()
        sharedDir.mkdirs()
        val target = File(sharedDir, sharedFileName(displayName))
        if (!exporter.export(uri, target)) return@withContext null
        FileProvider.getUriForFile(context, "${context.packageName}.files", target).toString()
    }
}
