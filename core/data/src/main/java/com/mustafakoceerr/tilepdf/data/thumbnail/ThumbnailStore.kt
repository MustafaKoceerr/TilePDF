package com.mustafakoceerr.tilepdf.data.thumbnail

import android.content.Context
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** A document's first page as a small PNG; [modifiedAt] changes when it's redrawn. */
data class Thumbnail(val file: File, val modifiedAt: Long)

/**
 * First-page images for the library, one PNG per document. Encoding is the
 * caller's: the store only keeps bytes. Excluded from backup, like private
 * copies: a received PDF's pages must not leave the device.
 */
@Singleton
class ThumbnailStore(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.noBackupFilesDir, "thumbnails"), ioDispatcher)

    suspend fun save(uri: String, png: ByteArray) = withContext(ioDispatcher) {
        dir.mkdirs()
        // Written aside and renamed: the library never decodes half a file.
        val temp = File.createTempFile("thumbnail", ".tmp", dir)
        try {
            temp.writeBytes(png)
            val target = fileFor(uri)
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
        } finally {
            temp.delete()
        }
    }

    /** The thumbnails there are for [uris]. */
    suspend fun find(uris: Collection<String>): Map<String, Thumbnail> = withContext(ioDispatcher) {
        val present = dir.listFiles().orEmpty().associateBy { it.name }
        uris.mapNotNull { uri ->
            present[fileFor(uri).name]?.let { uri to Thumbnail(it, it.lastModified()) }
        }.toMap()
    }

    suspend fun delete(uri: String) {
        withContext(ioDispatcher) { fileFor(uri).delete() }
    }

    /** Named by a hash: URIs hold characters file names can't. */
    private fun fileFor(uri: String): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.png")
    }
}
