package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.local.AnnotationDraftDao
import com.mustafakoceerr.tilepdf.data.local.AnnotationDraftEntity
import com.mustafakoceerr.tilepdf.data.local.RecognizedTextDao
import com.mustafakoceerr.tilepdf.data.local.RecognizedTextEntity
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.AnnotationDraft
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.OcrWord
import com.mustafakoceerr.tilepdf.model.SaveError
import com.mustafakoceerr.tilepdf.model.XfaEdit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class OfflineAnnotationRepository internal constructor(
    private val dao: AnnotationDraftDao,
    private val recognized: RecognizedTextDao,
    private val stamps: FileStamps,
    private val now: () -> Long,
) : AnnotationRepository {

    @Inject
    constructor(dao: AnnotationDraftDao, recognized: RecognizedTextDao, stamps: FileStamps) :
        this(dao, recognized, stamps, System::currentTimeMillis)

    override fun observe(uri: String): Flow<AnnotationDraft?> =
        combine(dao.observe(uri), recognized.observePages(uri)) { draft, pages ->
            draft?.toDraft()?.copy(recognizedPages = pages.toSet())
        }

    override suspend fun prepare(uri: String, document: PdfDocument): AnnotationDraft {
        val current = stamps.stampOrUnknown(uri)
        val stored = dao.get(uri)?.toDraft()
        if (stored != null && stored.base == current) return stored
        if (stored != null && stored.dirty) {
            return dao.update(uri) { it.copy(saveError = SaveError.FileChanged.name, updatedAt = now()) }!!.toDraft()
        }
        // No draft, or a clean one of an older file: what the file holds now is the draft.
        val scanned = AnnotationDraft(
            uri = uri,
            annotations = (0 until document.pageCount).flatMap { document.annotations(it).own },
            removedForeign = emptySet(),
            dirty = false,
            base = current,
            saveError = null,
        )
        dao.upsert(scanned.toEntity())
        return scanned
    }

    override suspend fun update(uri: String, annotations: List<Annotation>, removedForeign: Set<ForeignRef>) {
        dao.update(uri) {
            it.copy(
                annotations = AnnotationCodec.encodeAnnotations(annotations),
                removedForeign = AnnotationCodec.encodeRefs(removedForeign),
                dirty = true,
                updatedAt = now(),
            )
        }
    }

    override suspend fun setFormValue(uri: String, name: String, value: String) {
        dao.update(uri) {
            val values = AnnotationCodec.decodeFormValues(it.formValues) + (name to value)
            it.copy(formValues = AnnotationCodec.encodeFormValues(values), dirty = true, updatedAt = now())
        }
    }

    override suspend fun addXfaEdit(uri: String, edit: XfaEdit) {
        dao.update(uri) {
            val edits = AnnotationCodec.decodeXfaEdits(it.xfaEdits) + edit
            it.copy(xfaEdits = AnnotationCodec.encodeXfaEdits(edits), dirty = true, updatedAt = now())
        }
    }

    override suspend fun setRecognizedText(uri: String, pageIndex: Int, words: List<OcrWord>) {
        recognized.upsert(RecognizedTextEntity(uri, pageIndex, AnnotationCodec.encodeWords(words)))
        dao.update(uri) { it.copy(dirty = true, updatedAt = now()) }
    }

    override suspend fun recognizedText(uri: String, pages: Set<Int>): Map<Int, List<OcrWord>> =
        if (pages.isEmpty()) emptyMap() else recognized.get(uri, pages).associate { it.pageIndex to AnnotationCodec.decodeWords(it.words) }

    override suspend fun markSaved(uri: String, saved: AnnotationDraft, newBase: FileStamp) {
        val pending = pendingRecognizedPages(uri, saved)
        dao.update(uri) { it.afterSave(saved, newBase, pending) }
    }

    /** Drops the saved pages' recognised text; whether any is left to save. */
    private suspend fun pendingRecognizedPages(uri: String, saved: AnnotationDraft): Boolean {
        if (saved.recognizedPages.isNotEmpty()) recognized.delete(uri, saved.recognizedPages)
        return recognized.observePages(uri).first().isNotEmpty()
    }

    override suspend fun markFailed(uri: String, error: SaveError) {
        dao.update(uri) { it.copy(saveError = error.name, updatedAt = now()) }
    }

    override suspend fun moveTo(from: String, to: String, saved: AnnotationDraft, newBase: FileStamp) {
        val current = dao.get(from) ?: return
        val pending = pendingRecognizedPages(from, saved)
        dao.upsert(current.afterSave(saved, newBase, pending).copy(uri = to))
        recognized.move(from, to)
        dao.delete(from)
    }

    override suspend fun dirtyUris(): List<String> = dao.dirtyUris()

    /**
     * The draft once [saved] reached a file of version [newBase]. Saved
     * foreign refs are gone from the file, and `#index` refs no longer point
     * where they did, so both are dropped: a lost deletion is safer than a
     * wrong one.
     */
    private fun AnnotationDraftEntity.afterSave(
        saved: AnnotationDraft,
        newBase: FileStamp,
        recognizedPending: Boolean,
    ): AnnotationDraftEntity {
        val annotations = AnnotationCodec.decodeAnnotations(annotations)
        val removed = AnnotationCodec.decodeRefs(removedForeign) - saved.removedForeign
        val kept = removed.filterNot { it.key.startsWith("#") }.toSet()
        // Values changed again since the save started are still pending.
        val pendingValues = AnnotationCodec.decodeFormValues(formValues).filter { (name, value) -> saved.formValues[name] != value }
        // Edits made since the save started come after the saved ones.
        val edits = AnnotationCodec.decodeXfaEdits(xfaEdits)
        val pendingEdits = if (edits.take(saved.xfaEdits.size) == saved.xfaEdits) edits.drop(saved.xfaEdits.size) else edits
        return copy(
            removedForeign = AnnotationCodec.encodeRefs(kept),
            formValues = AnnotationCodec.encodeFormValues(pendingValues),
            xfaEdits = AnnotationCodec.encodeXfaEdits(pendingEdits),
            dirty = annotations != saved.annotations || kept.isNotEmpty() || pendingValues.isNotEmpty() ||
                pendingEdits.isNotEmpty() || recognizedPending,
            baseSize = newBase.size,
            baseModified = newBase.lastModified,
            saveError = null,
            updatedAt = now(),
        )
    }

    private fun AnnotationDraftEntity.toDraft() = AnnotationDraft(
        uri = uri,
        annotations = AnnotationCodec.decodeAnnotations(annotations),
        removedForeign = AnnotationCodec.decodeRefs(removedForeign),
        dirty = dirty,
        base = FileStamp(baseSize, baseModified),
        saveError = saveError?.let { name -> SaveError.entries.firstOrNull { it.name == name } },
        formValues = AnnotationCodec.decodeFormValues(formValues),
        xfaEdits = AnnotationCodec.decodeXfaEdits(xfaEdits),
    )

    private fun AnnotationDraft.toEntity() = AnnotationDraftEntity(
        uri = uri,
        annotations = AnnotationCodec.encodeAnnotations(annotations),
        removedForeign = AnnotationCodec.encodeRefs(removedForeign),
        dirty = dirty,
        baseSize = base.size,
        baseModified = base.lastModified,
        saveError = saveError?.name,
        updatedAt = now(),
        formValues = AnnotationCodec.encodeFormValues(formValues),
        xfaEdits = AnnotationCodec.encodeXfaEdits(xfaEdits),
    )
}
