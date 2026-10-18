package com.mustafakoceerr.tilepdf.data.signature

import android.content.Context
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.Signature
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's drawn signature, kept for the next document. Excluded from
 * backup: a signature stays on the device.
 */
@Singleton
class SignatureStore(
    private val file: File,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ) : this(File(context.noBackupFilesDir, "signature.json"), ioDispatcher)

    suspend fun save(signature: Signature) = withContext(ioDispatcher) {
        file.parentFile?.mkdirs()
        val json = SignatureJson(signature.strokes.map { stroke -> stroke.flatMap { listOf(it.x, it.y) } }, signature.height)
        // Written aside and renamed: a crash never leaves half a signature.
        val temp = File(file.path + ".tmp")
        temp.writeText(Json.encodeToString(json))
        if (!temp.renameTo(file)) {
            file.delete()
            temp.renameTo(file)
        }
    }

    /** The saved signature, or null when there is none or it can't be read. */
    suspend fun load(): Signature? = withContext(ioDispatcher) {
        try {
            val json = Json.decodeFromString<SignatureJson>(file.readText())
            Signature(json.strokes.map { flat -> flat.chunked(2) { (x, y) -> PdfPoint(x, y) } }, json.height)
        } catch (_: IOException) {
            null
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    suspend fun clear() {
        withContext(ioDispatcher) { file.delete() }
    }

    /** Each stroke as x, y pairs. */
    @Serializable
    private class SignatureJson(val strokes: List<List<Float>>, val height: Float)
}
