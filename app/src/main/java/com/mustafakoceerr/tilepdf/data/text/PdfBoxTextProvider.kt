package com.mustafakoceerr.tilepdf.data.text

import android.content.Context
import androidx.core.net.toUri
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.text.TextSearcher
import com.mustafakoceerr.tilepdf.di.IoDispatcher
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SearchMatch
import com.mustafakoceerr.tilepdf.model.TextChar
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import javax.inject.Inject

/**
 * Universal provider backed by PdfBox-Android; works on every supported API
 * level. Default choice below API 35.
 *
 * [PDDocument] is not thread-safe, so all access is serialized by [mutex].
 */
class PdfBoxTextProvider private constructor(
    private val document: PDDocument,
    private val ioDispatcher: CoroutineDispatcher,
) : PdfTextProvider {

    private val mutex = Mutex()
    private val pages = HashMap<Int, PageText>()
    private var closed = false

    override suspend fun pageText(pageIndex: Int): PageText = mutex.withLock {
        check(!closed) { "Provider is closed" }
        pages.getOrPut(pageIndex) { withContext(ioDispatcher) { extract(pageIndex) } }
    }

    override suspend fun search(pageIndex: Int, query: String): List<SearchMatch> =
        TextSearcher.search(pageText(pageIndex), query)

    override suspend fun close() = mutex.withLock {
        if (closed) return@withLock
        closed = true
        pages.clear()
        withContext(ioDispatcher) { document.close() }
    }

    private fun extract(pageIndex: Int): PageText {
        val stripper = CharCollector().apply {
            // PdfBox pages are 1-based.
            startPage = pageIndex + 1
            endPage = pageIndex + 1
            sortByPosition = true
        }
        stripper.getText(document)
        return PageText(pageIndex, stripper.chars)
    }

    /**
     * Collects every character with its box instead of building a string.
     * The *DirAdj coordinates already account for page rotation and are
     * relative to the crop box's top-left corner, which is exactly the space
     * PdfRenderer renders.
     */
    private class CharCollector : PDFTextStripper() {
        val chars = ArrayList<TextChar>()

        override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
            for (position in textPositions.orEmpty()) {
                val unicode = position.unicode ?: continue
                // Ligatures such as "fi" arrive as one glyph; split its width.
                val width = position.widthDirAdj / unicode.length
                val bottom = position.yDirAdj
                val top = bottom - position.heightDir
                unicode.forEachIndexed { i, char ->
                    val left = position.xDirAdj + i * width
                    chars += TextChar(char, PdfRect(left, top, left + width, bottom))
                }
            }
        }

        override fun writeWordSeparator() {
            chars += TextChar(' ', null)
        }

        override fun writeLineSeparator() {
            chars += TextChar('\n', null)
        }
    }

    class Factory @Inject constructor(
        @param:ApplicationContext private val context: Context,
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PdfTextProvider.Factory {

        override suspend fun create(uri: String): PdfTextProvider = withContext(ioDispatcher) {
            PDFBoxResourceLoader.init(context)
            val document = context.contentResolver.openInputStream(uri.toUri())?.use {
                // Buffer the file on disk rather than the heap: PDFs can be large.
                PDDocument.load(it, MemoryUsageSetting.setupTempFileOnly())
            } ?: throw FileNotFoundException("Cannot open $uri")
            PdfBoxTextProvider(document, ioDispatcher)
        }
    }
}
