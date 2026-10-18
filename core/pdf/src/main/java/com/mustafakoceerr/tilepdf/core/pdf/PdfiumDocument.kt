package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.OcrWord
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.PageAnnotations
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
        val native = withDocument(pageIndex) { PdfiumNative.nativePageText(it, pageIndex) }
            ?: return PageText.withoutBoxes(pageIndex, "")
        val text = native.text
        val boxes = native.boxes
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

    override suspend fun linkAt(pageIndex: Int, x: Float, y: Float): PdfLink? = withDocument(pageIndex) { handle ->
        val target = PdfiumNative.nativeLinkTargetAt(handle, pageIndex, x, y)
        if (target != null) {
            val page = target[0].toInt()
            val topPt = if (target[2].isNaN() || page !in 0 until pageCount) null else {
                PdfiumNative.nativePageToTopLeft(handle, page, floatArrayOf(target[1], target[2]))
                    ?.get(1)?.coerceIn(0f, pageSizes[page].heightPt.toFloat())
            }
            PdfLink.GoTo(page, topPt)
        } else {
            PdfiumNative.nativeLinkUriAt(handle, pageIndex, x, y)?.let(PdfLink::Uri)
        }
    }

    override suspend fun annotations(pageIndex: Int): PageAnnotations {
        val natives = withDocument(pageIndex) { PdfiumNative.nativePageAnnotations(it, pageIndex) }
            ?: return PageAnnotations(emptyList(), emptyList())
        return toPageAnnotations(pageIndex, natives.filterNotNull().toTypedArray())
    }

    override suspend fun setForeignHidden(pageIndex: Int, key: String, hidden: Boolean) {
        withDocument(pageIndex) { PdfiumNative.nativeSetForeignHidden(it, pageIndex, key, hidden) }
    }

    override suspend fun trimMemory() {
        try {
            withDocument { PdfiumNative.nativeTrim(it) }
        } catch (_: IllegalStateException) {
            // Already closed: nothing left to free.
        }
    }

    /** Pages PDFium currently keeps loaded for this document. */
    @VisibleForTesting
    internal suspend fun loadedPageCount(): Int = withDocument { PdfiumNative.nativeLoadedPageCount(it) }

    @VisibleForTesting
    internal suspend fun annotationCount(pageIndex: Int): Int =
        withDocument(pageIndex) { PdfiumNative.nativeAnnotationCount(it, pageIndex) }

    /** Runs [block] with the native handle, after checking [pageIndex] if given. */
    override suspend fun hasForm(): Boolean = withDocument { PdfiumNative.nativeHasForm(it) }

    override suspend fun formFields(pageIndex: Int): List<FormField> =
        withDocument(pageIndex) { PdfiumNative.nativeFormFields(it, pageIndex) }
            ?.mapNotNull { it?.toFormField(pageIndex) }
            ?: throw PdfCorruptException("Page $pageIndex could not be loaded")

    override suspend fun applyFormEdits(edits: List<FormEdit>): Boolean = edits.all { edit ->
        withDocument(edit.pageIndex) { handle ->
            when (edit) {
                is FormEdit.SetText -> PdfiumNative.nativeSetFormText(handle, edit.pageIndex, edit.annotIndex, edit.text)
                is FormEdit.SetChecked -> PdfiumNative.nativeSetFormChecked(handle, edit.pageIndex, edit.annotIndex, edit.checked)
                is FormEdit.SetChoice -> PdfiumNative.nativeSetFormChoice(handle, edit.pageIndex, edit.annotIndex, edit.optionIndex)
            }
        }
    }

    override suspend fun hasRecognizedText(pageIndex: Int): Boolean =
        withDocument(pageIndex) { PdfiumNative.nativeHasRecognizedText(it, pageIndex) }

    override suspend fun addRecognizedText(pageIndex: Int, words: List<OcrWord>) {
        if (words.isEmpty()) return
        val texts = words.map { it.text }.toTypedArray()
        val quads = words.flatMap { word -> word.quad.flatMap { listOf(it.x, it.y) } }.toFloatArray()
        val added = withDocument(pageIndex) { PdfiumNative.nativeAddRecognizedText(it, pageIndex, texts, quads) }
        if (!added) throw PdfWriteException("Could not add recognised text to page $pageIndex")
    }

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
