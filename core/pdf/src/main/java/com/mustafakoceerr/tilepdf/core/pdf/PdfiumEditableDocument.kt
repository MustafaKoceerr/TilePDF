package com.mustafakoceerr.tilepdf.core.pdf

import android.os.ParcelFileDescriptor
import androidx.annotation.VisibleForTesting
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.OcrWord
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.TextMarkup
import java.io.File

/**
 * An [EditablePdfDocument] on PDFium. Shares [document]'s dispatcher, mutex
 * and lifetime; [document] was opened without hiding anything.
 */
class PdfiumEditableDocument internal constructor(
    /** Draws the file as any viewer would: nothing is hidden. */
    @get:VisibleForTesting internal val document: PdfiumDocument,
) : EditablePdfDocument {

    override val pageCount: Int get() = document.pageCount

    override suspend fun ownAnnotationIds(pageIndex: Int): Set<String> =
        document.withDocument(pageIndex) { PdfiumNative.nativeOwnAnnotationIds(it, pageIndex) }
            ?.toSet() ?: throw PdfCorruptException("Page $pageIndex could not be loaded")

    override suspend fun foreignKeys(pageIndex: Int): Set<String> =
        document.withDocument(pageIndex) { PdfiumNative.nativeForeignKeys(it, pageIndex) }
            ?.toSet() ?: throw PdfCorruptException("Page $pageIndex could not be loaded")

    override suspend fun apply(changes: AnnotationChanges) {
        val touched = sortedSetOf<Int>()
        for (page in changes.removals.keys + changes.foreignRemovals.keys) {
            val own = changes.removals[page].orEmpty().toTypedArray()
            val foreign = changes.foreignRemovals[page].orEmpty().toTypedArray()
            val removed = document.withDocument(page) { PdfiumNative.nativeRemoveAnnotations(it, page, own, foreign) }
            if (!removed) throw PdfWriteException("Could not remove annotations on page $page")
            touched += page
        }
        for (annotation in changes.additions) {
            if (!add(annotation)) throw PdfWriteException("Could not add ${annotation.id} on page ${annotation.pageIndex}")
            touched += annotation.pageIndex
        }
        for (page in touched) {
            document.withDocument(page) { PdfiumNative.nativeGenerateAppearances(it, page) }
        }
    }

    private suspend fun add(annotation: Annotation): Boolean = document.withDocument(annotation.pageIndex) { handle ->
        val page = annotation.pageIndex
        when (annotation) {
            is TextMarkup -> PdfiumNative.nativeAddMarkup(
                handle, page, annotation.kind.subtype, annotation.color, annotation.quads(), annotation.id, annotation.text,
            )
            is Ink -> PdfiumNative.nativeAddInk(
                handle, page, annotation.color, annotation.widthPt,
                annotation.strokes.flatten().flatMap { listOf(it.x, it.y) }.toFloatArray(),
                annotation.strokes.map { it.size }.toIntArray(),
                annotation.id,
            )
            is Note -> PdfiumNative.nativeAddNote(
                handle, page, annotation.color, annotation.at.x, annotation.at.y, annotation.id, annotation.text,
            )
        }
    }

    override suspend fun hasForm(): Boolean = document.hasForm()

    override suspend fun formFields(pageIndex: Int): List<FormField> = document.formFields(pageIndex)

    override suspend fun applyFormEdits(edits: List<FormEdit>): Boolean = document.applyFormEdits(edits)

    override suspend fun setScriptHost(host: FormScriptHost?, openAction: Boolean) = document.setScriptHost(host, openAction)

    override suspend fun isXfa(): Boolean = document.isXfa()

    override suspend fun xfaTap(pageIndex: Int, at: PdfPoint): String? = document.xfaTap(pageIndex, at)

    override suspend fun xfaWrite(pageIndex: Int, text: String) = document.xfaWrite(pageIndex, text)

    override suspend fun hasRecognizedText(pageIndex: Int): Boolean = document.hasRecognizedText(pageIndex)

    override suspend fun addRecognizedText(pageIndex: Int, words: List<OcrWord>) = document.addRecognizedText(pageIndex, words)

    override suspend fun addSignatureStamp(pageIndex: Int, name: String, appearance: SignatureAppearance) {
        val rect = appearance.rect
        val added = document.withDocument(pageIndex) { handle ->
            PdfiumNative.nativeAddSignatureStamp(
                handle, pageIndex,
                floatArrayOf(rect.left, rect.top, rect.right, rect.bottom),
                name,
                appearance.lines.map { it.text }.toTypedArray(),
                appearance.lines.flatMap { listOf(it.x, it.baseline, it.size) }.toFloatArray(),
                appearance.strokes.flatten().flatMap { listOf(it.x, it.y) }.toFloatArray(),
                appearance.strokes.map { it.size }.toIntArray(),
                appearance.strokeWidth,
            )
        }
        if (!added) throw PdfWriteException("Could not add the signature box on page $pageIndex")
    }

    override suspend fun saveIncremental(target: File) {
        val saved = ParcelFileDescriptor.open(
            target,
            ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE,
        ).use { output -> document.withDocument { PdfiumNative.nativeSaveIncremental(it, output.fd) } }
        if (!saved) throw PdfWriteException("Could not save to $target")
    }

    override fun close() = document.close()
}

private val MarkupKind.subtype: Int
    get() = when (this) {
        MarkupKind.Highlight -> AnnotSubtype.HIGHLIGHT
        MarkupKind.Underline -> AnnotSubtype.UNDERLINE
        MarkupKind.StrikeOut -> AnnotSubtype.STRIKEOUT
    }

/** Each line rect as its top-left, top-right, bottom-left and bottom-right corners. */
private fun TextMarkup.quads(): FloatArray = rects.flatMap { r ->
    listOf(r.left, r.top, r.right, r.top, r.left, r.bottom, r.right, r.bottom)
}.toFloatArray()
