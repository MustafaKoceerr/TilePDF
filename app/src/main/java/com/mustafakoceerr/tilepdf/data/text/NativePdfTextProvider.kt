package com.mustafakoceerr.tilepdf.data.text

import android.content.Context
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * Provider backed by the platform's PDFium text APIs (API 35+): no extra
 * library, and search runs natively instead of over text copied into Kotlin.
 *
 * Differences from [PdfBoxTextProvider], by design of the platform API:
 * - [pageText] has no per-character boxes (the platform only reports block
 *   bounds), so its chars carry `null` boxes; search doesn't need them.
 * - Search uses PDFium's own matching: case-insensitive, but without the
 *   Turkish dotted/dotless i folding [com.mustafakoceerr.tilepdf.core.text.TextSearcher] does.
 *
 * Uses its own [PdfRenderer] so text work never waits behind tile rendering.
 */
@RequiresApi(35)
class NativePdfTextProvider private constructor(
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfTextProvider {

    private val mutex = Mutex()
    private val pages = HashMap<Int, PageText>()
    private var closed = false

    override suspend fun pageText(pageIndex: Int): PageText = mutex.withLock {
        check(!closed) { "Provider is closed" }
        pages.getOrPut(pageIndex) {
            withContext(ioDispatcher) {
                val text = renderer.openPage(pageIndex).use { page ->
                    page.textContents.joinToString("\n") { it.text }
                }
                PageText.withoutBoxes(pageIndex, text)
            }
        }
    }

    override suspend fun search(pageIndex: Int, query: String): List<SearchMatch> {
        // Collapse whitespace like TextSearcher does; PDFium matches literally.
        val needle = query.trim().replace(WHITESPACE, " ")
        if (needle.isEmpty()) return emptyList()
        val found = mutex.withLock {
            check(!closed) { "Provider is closed" }
            withContext(ioDispatcher) {
                renderer.openPage(pageIndex).use { it.searchText(needle) }
            }
        }
        // Bounds are already per line, in top-left page points.
        return found.map { match -> SearchMatch(pageIndex, match.bounds.map { it.toPdfRect() }) }
    }

    override suspend fun close() = mutex.withLock {
        if (closed) return@withLock
        closed = true
        pages.clear()
        withContext(ioDispatcher) {
            renderer.close()
            fileDescriptor.close()
        }
    }

    class Factory @Inject constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PdfTextProvider.Factory {

        @RequiresApi(35)
        override suspend fun create(uri: String): PdfTextProvider = withContext(ioDispatcher) {
            val fileDescriptor = context.contentResolver.openFileDescriptor(uri.toUri(), "r")
                ?: throw FileNotFoundException("Cannot open $uri")
            try {
                NativePdfTextProvider(fileDescriptor, PdfRenderer(fileDescriptor), ioDispatcher)
            } catch (e: Exception) {
                fileDescriptor.close()
                throw e
            }
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")

        fun RectF.toPdfRect() = PdfRect(left, top, right, bottom)
    }
}
