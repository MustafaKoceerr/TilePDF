package com.mustafakoceerr.tilepdf.data.signature

import android.content.Context
import androidx.core.net.toUri
import com.mustafakoceerr.tilepdf.core.pdf.PdfSignature
import com.mustafakoceerr.tilepdf.core.signature.SignatureVerifier
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.SignatureCheck
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.nio.ByteBuffer
import javax.inject.Inject

/** Read 64 KB at a time: a signed range may be most of a large file. */
private const val CHUNK_BYTES = 64 * 1024

/** Checks a document's signatures against the bytes of its file. */
class DocumentSignatures @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val verifier: SignatureVerifier,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Each of [signatures] checked against [uri]; none if the file can't be read. */
    suspend fun check(uri: String, signatures: List<PdfSignature>): List<SignatureCheck> = withContext(ioDispatcher) {
        if (signatures.isEmpty()) return@withContext emptyList()
        val descriptor = runCatching { context.contentResolver.openFileDescriptor(uri.toUri(), "r") }.getOrNull()
            ?: return@withContext emptyList()
        descriptor.use { file ->
            FileInputStream(file.fileDescriptor).channel.use { channel ->
                val length = file.statSize.takeIf { it >= 0 } ?: channel.size()
                signatures.map { signature ->
                    verifier.verify(signature, length) { offset, count, sink ->
                        val buffer = ByteBuffer.allocate(CHUNK_BYTES)
                        var position = offset
                        val end = offset + count
                        while (position < end) {
                            buffer.clear()
                            buffer.limit(minOf(CHUNK_BYTES.toLong(), end - position).toInt())
                            val read = channel.read(buffer, position)
                            if (read <= 0) break
                            sink(buffer.array().copyOf(read))
                            position += read
                        }
                    }
                }
            }
        }
    }
}
