package com.mustafakoceerr.tilepdf.data.document

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

/**
 * Serves the test PDFs the way other apps' providers do:
 * - `sized/<name>`: a real file descriptor with a known size;
 * - `pipe/<name>`: a pipe with no size, written in 4 KB chunks;
 * - `broken/<name>`: a pipe that fails after 1 KB;
 * - `throwing/<name>`: a provider that throws an unchecked exception.
 */
class TestDocumentsProvider : ContentProvider() {

    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val name = uri.lastPathSegment!!
        return when (uri.pathSegments.first()) {
            "sized" -> ParcelFileDescriptor.open(materialize(name), ParcelFileDescriptor.MODE_READ_ONLY)
            "pipe" -> pipe(name, failAfter = null)
            "broken" -> pipe(name, failAfter = 1024)
            // A misbehaving provider: its exception crosses Binder into the caller.
            "throwing" -> throw IllegalArgumentException("Provider bug for $uri")
            else -> throw IllegalArgumentException("Unknown $uri")
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor =
        MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply {
            val name = uri.lastPathSegment!!
            addRow(arrayOf<Any?>(name, if (uri.pathSegments.first() == "sized") materialize(name).length() else null))
        }

    override fun getType(uri: Uri) = "application/pdf"

    private fun materialize(name: String): File =
        File(context!!.cacheDir, "provider-$name").apply {
            if (!exists()) context!!.assets.open(name).use { input -> outputStream().use { input.copyTo(it) } }
        }

    private fun pipe(name: String, failAfter: Int?): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        thread {
            try {
                writeAll(name, write, failAfter)
            } catch (_: IOException) {
                // The reader closed early, as readers may.
            }
        }
        return read
    }

    private fun writeAll(name: String, write: ParcelFileDescriptor, failAfter: Int?) {
        context!!.assets.open(name).use { input ->
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { output ->
                val buffer = ByteArray(4096)
                var sent = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (failAfter != null && sent + count > failAfter) {
                        write.closeWithError("Provider failed mid-stream")
                        return
                    }
                    output.write(buffer, 0, count)
                    sent += count
                }
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?) = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = throw UnsupportedOperationException()

    companion object {
        const val AUTHORITY = "com.mustafakoceerr.tilepdf.core.data.test.files"

        fun uri(kind: String, name: String): String = "content://$AUTHORITY/$kind/$name"
    }
}
