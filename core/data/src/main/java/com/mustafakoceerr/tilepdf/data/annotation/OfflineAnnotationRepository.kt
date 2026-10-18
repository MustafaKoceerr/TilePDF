package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.local.AnnotationDraftDao
import com.mustafakoceerr.tilepdf.data.local.AnnotationDraftEntity
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.AnnotationDraft
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineAnnotationRepository internal constructor(
    private val dao: AnnotationDraftDao,
    private val stamps: FileStamps,
    private val now: () -> Long,
) : AnnotationRepository {

    @Inject
    constructor(dao: AnnotationDraftDao, stamps: FileStamps) : this(dao, stamps, System::currentTimeMillis)

    override fun observe(uri: String): Flow<AnnotationDraft?> = dao.observe(uri).map { it?.toDraft() }

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

    override suspend fun markSaved(uri: String, saved: AnnotationDraft, newBase: FileStamp) {
        dao.update(uri) { it.afterSave(saved, newBase) }
    }

    override suspend fun markFailed(uri: String, error: SaveError) {
        dao.update(uri) { it.copy(saveError = error.name, updatedAt = now()) }
    }

    override suspend fun moveTo(from: String, to: String, saved: AnnotationDraft, newBase: FileStamp) {
        val current = dao.get(from) ?: return
        dao.upsert(current.afterSave(saved, newBase).copy(uri = to))
        dao.delete(from)
    }

    override suspend fun dirtyUris(): List<String> = dao.dirtyUris()

    /**
     * The draft once [saved] reached a file of version [newBase]. Saved
     * foreign refs are gone from the file, and `#index` refs no longer point
     * where they did, so both are dropped: a lost deletion is safer than a
     * wrong one.
     */
    private fun AnnotationDraftEntity.afterSave(saved: AnnotationDraft, newBase: FileStamp): AnnotationDraftEntity {
        val annotations = AnnotationCodec.decodeAnnotations(annotations)
        val removed = AnnotationCodec.decodeRefs(removedForeign) - saved.removedForeign
        val kept = removed.filterNot { it.key.startsWith("#") }.toSet()
        // Values changed again since the save started are still pending.
        val pendingValues = AnnotationCodec.decodeFormValues(formValues).filter { (name, value) -> saved.formValues[name] != value }
        return copy(
            removedForeign = AnnotationCodec.encodeRefs(kept),
            formValues = AnnotationCodec.encodeFormValues(pendingValues),
            dirty = annotations != saved.annotations || kept.isNotEmpty() || pendingValues.isNotEmpty(),
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
    )
}
