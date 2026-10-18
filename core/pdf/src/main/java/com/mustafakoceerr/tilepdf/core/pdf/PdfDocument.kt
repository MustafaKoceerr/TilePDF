package com.mustafakoceerr.tilepdf.core.pdf

import android.graphics.Bitmap
import android.graphics.Matrix
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.PageText
import java.io.Closeable

/**
 * An open PDF. One instance serves rendering, text and the outline for as
 * long as a reader shows it. Calls after [close] throw [IllegalStateException].
 */
interface PdfDocument : Closeable {

    /** Size of every page in points, read once when the document is opened. */
    val pageSizes: List<PageSize>

    val pageCount: Int get() = pageSizes.size

    /**
     * Renders [pageIndex] into an ARGB_8888 [bitmap]. [transform] maps top-left
     * page points to bitmap pixels; the caller is responsible for clearing the
     * bitmap.
     */
    suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix)

    /** The text of [pageIndex] with a box per character in top-left points. */
    suspend fun pageText(pageIndex: Int): PageText

    /** The table of contents, flattened depth first; empty if there is none. */
    suspend fun outline(): List<OutlineEntry>

    /** The link at top-left point ([x], [y]) on [pageIndex], if any. */
    suspend fun linkAt(pageIndex: Int, x: Float, y: Float): PdfLink?

    /**
     * Frees what the engine keeps to make the next render faster, such as
     * parsed pages. Called when the app is low on memory or hidden.
     */
    suspend fun trimMemory()
}
