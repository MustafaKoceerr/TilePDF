package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.pdf.PdfPasswordException
import com.mustafakoceerr.tilepdf.core.pdf.write
import com.mustafakoceerr.tilepdf.model.AnnotationDraft
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject

sealed interface SaveResult {
    data object NothingToSave : SaveResult
    data class Saved(val uri: String) : SaveResult
    data class Failed(val error: SaveError) : SaveResult
}

/** Puts annotation drafts into files. */
interface AnnotationSaving {

    /** Writes the draft of [uri] into [uri]; a failure is recorded in the draft. */
    suspend fun save(uri: String): SaveResult

    /**
     * Writes the draft of [uri] into the new document [target] and moves the
     * draft there. A failure leaves the draft as it was.
     */
    suspend fun saveCopy(uri: String, target: String): SaveResult
}

/**
 * The safe save: write a temp file with PDFium, reopen it to check it holds
 * exactly the draft, and only then overwrite the document. The document is
 * never touched when anything before the overwrite fails.
 */
class AnnotationSaver @Inject constructor(
    private val repository: AnnotationRepository,
    private val files: AnnotationFiles,
    private val passwords: SessionPasswords,
) : AnnotationSaving {

    override suspend fun save(uri: String): SaveResult {
        val draft = repository.observe(uri).first()
        if (draft == null || !draft.dirty) return SaveResult.NothingToSave
        // Another app changed the file: overwriting would lose its changes.
        if (files.stamp(uri) != draft.base) return failed(uri, SaveError.FileChanged)
        return when (val outcome = write(uri, draft, draft.removedForeign, target = uri)) {
            Outcome.Unchanged -> {
                repository.markSaved(uri, draft, draft.base)
                SaveResult.Saved(uri)
            }
            is Outcome.Written -> {
                repository.markSaved(uri, draft, outcome.stamp)
                SaveResult.Saved(uri)
            }
            is Outcome.Error -> failed(uri, outcome.error)
        }
    }

    override suspend fun saveCopy(uri: String, target: String): SaveResult {
        val draft = repository.observe(uri).first() ?: return SaveResult.NothingToSave
        // "#index" keys only hold for the file version the draft is based on.
        val removed = if (files.stamp(uri) == draft.base) {
            draft.removedForeign
        } else {
            draft.removedForeign.filterNot { it.key.startsWith("#") }.toSet()
        }
        return when (val outcome = write(uri, draft, removed, target)) {
            is Outcome.Written -> {
                repository.moveTo(uri, target, draft, outcome.stamp)
                passwords.moved(uri, target)
                SaveResult.Saved(target)
            }
            is Outcome.Error -> SaveResult.Failed(outcome.error)
            Outcome.Unchanged -> error("A copy is always written")
        }
    }

    private suspend fun failed(uri: String, error: SaveError): SaveResult {
        repository.markFailed(uri, error)
        return SaveResult.Failed(error)
    }

    private sealed interface Outcome {
        data object Unchanged : Outcome
        data class Written(val stamp: FileStamp) : Outcome
        data class Error(val error: SaveError) : Outcome
    }

    /** Writes [draft] from [source] into [target] through a checked temp file. */
    private suspend fun write(source: String, draft: AnnotationDraft, removed: Set<ForeignRef>, target: String): Outcome {
        val password = passwords.get(source)
        val temp = files.newTempFile()
        try {
            val changed = try {
                files.openForEditing(source, password).use { document ->
                    val changed = document.write(draft.annotations, removed)
                    if (changed || target != source) document.saveIncremental(temp)
                    changed
                }
            } catch (_: PdfPasswordException) {
                return Outcome.Error(SaveError.NeedsPassword)
            }
            if (!changed && target == source) return Outcome.Unchanged
            if (!holdsExactly(temp, password, draft, removed)) return Outcome.Error(SaveError.Failed)
            try {
                files.replace(target, temp)
            } catch (_: SecurityException) {
                return Outcome.Error(SaveError.NoWritePermission)
            } catch (_: FileNotFoundException) {
                return Outcome.Error(SaveError.NoWritePermission)
            } catch (_: UnsupportedOperationException) {
                return Outcome.Error(SaveError.NoWritePermission)
            }
            return Outcome.Written(files.stamp(target) ?: FileStamp(temp.length(), null))
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            return Outcome.Error(SaveError.Failed)
        } catch (_: SecurityException) {
            // The read grant on the source was revoked.
            return Outcome.Error(SaveError.Failed)
        } finally {
            temp.delete()
        }
    }

    /** Whether the saved [file] has exactly the draft's own annotations and none of its removed named ones. */
    private suspend fun holdsExactly(file: File, password: String?, draft: AnnotationDraft, removed: Set<ForeignRef>): Boolean =
        files.openForReading(file, password).use { document ->
            val pages = (0 until document.pageCount).map { document.annotations(it) }
            val ownIds = pages.flatMap { page -> page.own.map { it.id } }.toSet()
            val foreign = pages.flatMap { page -> page.foreign.map { it.ref } }.toSet()
            ownIds == draft.annotations.map { it.id }.toSet() &&
                removed.none { !it.key.startsWith("#") && it in foreign }
        }
}
