package com.mustafakoceerr.tilepdf.data.text

import android.graphics.Bitmap
import android.graphics.Matrix
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.PageAnnotations
import com.mustafakoceerr.tilepdf.model.PageText

/** A document whose pages hold [pages]; char `i` of a page sits at x = i. */
class FakePdfDocument(private val pages: List<String>) : PdfDocument {

    var pageTextCalls = 0
        private set

    private var closed = false

    /** Runs before each page is extracted; lets a test act mid-search. */
    var beforePageText: (pageIndex: Int) -> Unit = {}

    override val pageSizes = List(pages.size) { PageSize(595, 842) }

    override suspend fun pageText(pageIndex: Int): PageText {
        beforePageText(pageIndex)
        check(!closed) { "Document is closed" }
        pageTextCalls++
        val builder = PageText.Builder()
        pages[pageIndex].forEachIndexed { i, char -> builder.append(char, PdfRect(i.toFloat(), 0f, i + 1f, 10f)) }
        return builder.build(pageIndex)
    }

    override suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) = error("unused")

    override suspend fun outline(): List<OutlineEntry> = error("unused")

    override suspend fun trimMemory() = Unit

    /** Text fields on page 0 by name. */
    var form: Map<String, String> = emptyMap()

    override suspend fun hasForm() = form.isNotEmpty()

    override suspend fun formFields(pageIndex: Int) =
        if (pageIndex == 0) com.mustafakoceerr.tilepdf.data.annotation.formFieldsOf(form) else emptyList()
    override suspend fun linkAt(pageIndex: Int, x: Float, y: Float): com.mustafakoceerr.tilepdf.core.pdf.PdfLink? = null
    /** What [annotations] returns per page; pages not listed have none. */
    var annotationsByPage: Map<Int, PageAnnotations> = emptyMap()

    var annotationCalls = 0
        private set

    override suspend fun annotations(pageIndex: Int): PageAnnotations {
        annotationCalls++
        return annotationsByPage[pageIndex] ?: PageAnnotations(emptyList(), emptyList())
    }
    override suspend fun setForeignHidden(pageIndex: Int, key: String, hidden: Boolean) = Unit

    override fun close() {
        closed = true
    }
}
