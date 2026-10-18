package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.pdf.AnnotationChanges
import com.mustafakoceerr.tilepdf.core.pdf.EditablePdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfPasswordException
import com.mustafakoceerr.tilepdf.data.text.FakePdfDocument
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.model.PageAnnotations
import java.io.File

/** A 3-page PDF's annotations: ours, and other apps' keys per page. */
data class FakePdf(val own: List<Annotation> = emptyList(), val foreign: Map<Int, Set<String>> = emptyMap())

/**
 * Files as [AnnotationSaver] sees them, in memory. Saved temp files are real
 * (so deletion can be checked) and remember the [FakePdf] written into them.
 */
class FakeAnnotationFiles(private val tempDir: File) : AnnotationFiles {

    val pdfs = mutableMapOf<String, FakePdf>()
    val stamps = mutableMapOf<String, FileStamp>()
    val passwords = mutableMapOf<String, String>()
    private val saved = mutableMapOf<File, FakePdf>()

    val temps = mutableListOf<File>()
    val replaced = mutableListOf<String>()
    var editOpens = 0
        private set

    /** Applied changes, for checking what the saver asked for. */
    val applied = mutableListOf<AnnotationChanges>()

    /** Thrown by [replace] instead of writing. */
    var replaceFailure: Exception? = null

    /** Drops this annotation id when saving, as a broken save would. */
    var loseOnSave: String? = null

    override suspend fun stamp(uri: String): FileStamp? = stamps[uri]

    override suspend fun openForEditing(uri: String, password: String?): EditablePdfDocument {
        editOpens++
        val required = passwords[uri]
        if (required != null && required != password) throw PdfPasswordException(wrongPassword = password != null)
        return Editable(pdfs.getValue(uri))
    }

    override suspend fun openForReading(file: File, password: String?): PdfDocument {
        val pdf = saved.getValue(file)
        return FakePdfDocument(List(PAGES) { "" }).apply {
            annotationsByPage = (0 until PAGES).associateWith { page ->
                PageAnnotations(
                    own = pdf.own.filter { it.pageIndex == page },
                    foreign = pdf.foreign[page].orEmpty().map { ForeignAnnotation(page, it, 9, PdfRect(0f, 0f, 1f, 1f), "", emptyList()) },
                )
            }
        }
    }

    override fun newTempFile(): File = File.createTempFile("save", ".pdf", tempDir).also { temps += it }

    override suspend fun replace(uri: String, with: File) {
        replaceFailure?.let { throw it }
        pdfs[uri] = saved.getValue(with)
        stamps[uri] = FileStamp((stamps[uri]?.size ?: 0) + 1, 99)
        replaced += uri
    }

    private inner class Editable(private var pdf: FakePdf) : EditablePdfDocument {
        override val pageCount = PAGES
        override suspend fun ownAnnotationIds(pageIndex: Int) = pdf.own.filter { it.pageIndex == pageIndex }.map { it.id }.toSet()
        override suspend fun foreignKeys(pageIndex: Int) = pdf.foreign[pageIndex].orEmpty()

        override suspend fun apply(changes: AnnotationChanges) {
            applied += changes
            val removed = changes.removals.values.flatten().toSet()
            pdf = FakePdf(
                own = pdf.own.filterNot { it.id in removed } + changes.additions,
                foreign = pdf.foreign.mapValues { (page, keys) -> keys - changes.foreignRemovals[page].orEmpty() },
            )
        }

        override suspend fun saveIncremental(target: File) {
            target.writeText("pdf")
            saved[target] = pdf.copy(own = pdf.own.filterNot { it.id == loseOnSave })
        }

        override fun close() = Unit
    }

    private companion object {
        const val PAGES = 3
    }
}
