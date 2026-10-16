package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.ParcelFileDescriptor
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.PageText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An open PDF backed by PDFium. Every call runs on the shared PDF dispatcher
 * under a [Mutex], so closing waits for in-flight work and nothing touches
 * the native document afterwards.
 */
class PdfiumDocument internal constructor(
    private val fileDescriptor: ParcelFileDescriptor,
    private val handle: Long,
    override val pageSizes: List<PageSize>,
    private val dispatcher: CoroutineDispatcher,
) : PdfDocument {

    private val mutex = Mutex()
    private var closed = false

    /**
     * Renders [pageIndex] into [bitmap]. [transform] maps top-left page points
     * to bitmap pixels; the caller is responsible for clearing the bitmap.
     */
    override suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) {
        require(bitmap.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888, was ${bitmap.config}" }
        val values = FloatArray(9).also(transform::getValues)
        // Android's Matrix is row-major 3x3; PDFium's is {a, b, c, d, e, f}
        // with x' = a*x + c*y + e and y' = b*x + d*y + f.
        val matrix = floatArrayOf(
            values[Matrix.MSCALE_X], values[Matrix.MSKEW_Y],
            values[Matrix.MSKEW_X], values[Matrix.MSCALE_Y],
            values[Matrix.MTRANS_X], values[Matrix.MTRANS_Y],
        )
        val rendered = withDocument(pageIndex) { PdfiumNative.nativeRender(it, pageIndex, bitmap, matrix) }
        if (!rendered) throw PdfCorruptException("Page $pageIndex could not be rendered")
    }

    /**
     * The text of [pageIndex] with a box per character in top-left points.
     * Line breaks are plain `'\n'`; characters PDFium generates (spaces and
     * line breaks it inferred) have no box.
     */
    override suspend fun pageText(pageIndex: Int): PageText {
        val (text, boxes) = withDocument(pageIndex) {
            PdfiumNative.nativePageText(it, pageIndex) to PdfiumNative.nativeCharBoxes(it, pageIndex)
        }
        if (text == null || boxes == null) return PageText.withoutBoxes(pageIndex, "")
        val builder = PageText.Builder(text.length)
        text.forEachIndexed { i, char ->
            if (char == '\r') return@forEachIndexed
            val offset = i * 4
            val box = if (boxes[offset].isNaN()) null else {
                PdfRect(boxes[offset], boxes[offset + 1], boxes[offset + 2], boxes[offset + 3])
            }
            builder.append(char, box)
        }
        return builder.build(pageIndex)
    }

    /**
     * The table of contents, flattened depth first. Entries with a blank title
     * or no target page are skipped but their children are kept; `topPt` is
     * set only for destinations that give a y.
     *
     * Bookmarks are read in one pass that loads no page. Mapping a y to the
     * top-left needs the page loaded, so that happens one page per call, and
     * renders waiting on the PDF dispatcher run in between.
     */
    override suspend fun outline(): List<OutlineEntry> {
        val raw = withDocument { handle -> readBookmarks(handle) }
        val tops = HashMap<Int, FloatArray?>()
        for ((pageIndex, entries) in raw.filter { !it.y.isNaN() }.groupBy { it.pageIndex }) {
            val points = FloatArray(entries.size * 2).also { points ->
                entries.forEachIndexed { i, entry -> points[i * 2] = entry.x; points[i * 2 + 1] = entry.y }
            }
            tops[pageIndex] = withDocument { PdfiumNative.nativePageToTopLeft(it, pageIndex, points) }
        }
        val nextOnPage = HashMap<Int, Int>()
        return raw.map { entry ->
            val topPt = if (entry.y.isNaN()) null else {
                val slot = nextOnPage.merge(entry.pageIndex, 1, Int::plus)!! - 1
                tops[entry.pageIndex]?.get(slot * 2 + 1)?.coerceIn(0f, pageSizes[entry.pageIndex].heightPt.toFloat())
            }
            OutlineEntry(entry.title, entry.pageIndex, topPt, entry.depth)
        }
    }

    private class RawBookmark(val title: String, val pageIndex: Int, val x: Float, val y: Float, val depth: Int)

    private fun readBookmarks(handle: Long): List<RawBookmark> {
        val entries = mutableListOf<RawBookmark>()
        // Malformed files can link bookmarks into a cycle.
        val visited = HashSet<Long>()

        fun addChildren(parent: Long, depth: Int) {
            var bookmark = PdfiumNative.nativeBookmarkFirstChild(handle, parent)
            while (bookmark != 0L && visited.add(bookmark) && entries.size < MAX_OUTLINE_ENTRIES) {
                val title = PdfiumNative.nativeBookmarkTitle(bookmark).trim()
                val (page, x, y) = PdfiumNative.nativeBookmarkTarget(handle, bookmark)
                val pageIndex = page.toInt()
                if (title.isNotEmpty() && pageIndex in 0 until pageCount) {
                    entries += RawBookmark(title, pageIndex, x, y, depth)
                }
                addChildren(bookmark, depth + 1)
                bookmark = PdfiumNative.nativeBookmarkNextSibling(handle, bookmark)
            }
        }

        addChildren(parent = 0L, depth = 0)
        return entries
    }

    /** Runs [block] with the native handle, after checking [pageIndex] if given. */
    internal suspend fun <T> withDocument(pageIndex: Int? = null, block: (Long) -> T): T {
        if (pageIndex != null) {
            require(pageIndex in 0 until pageCount) { "Page $pageIndex out of 0 until $pageCount" }
        }
        return withContext(dispatcher) {
            mutex.withLock {
                check(!closed) { "Document is closed" }
                block(handle)
            }
        }
    }

    /**
     * Closes the document once any in-flight call finishes. Safe to call from
     * non-suspending contexts such as `ViewModel.onCleared`.
     */
    override fun close() {
        CoroutineScope(dispatcher + NonCancellable).launch {
            mutex.withLock {
                if (closed) return@withLock
                closed = true
                PdfiumNative.nativeCloseDocument(handle)
                fileDescriptor.close()
            }
        }
    }
}

/** Stops pathological outlines from flooding the table of contents. */
private const val MAX_OUTLINE_ENTRIES = 10_000
