package com.mustafakoceerr.tilepdf.feature.reader.annotation

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationRepository
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaveScheduler
import com.mustafakoceerr.tilepdf.domain.AnnotationHistory
import com.mustafakoceerr.tilepdf.domain.AnnotationState
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What the reader shows of a document's annotations. */
data class AnnotationsUiState(
    /** False until the draft is loaded; editing waits for it. */
    val ready: Boolean = false,
    val own: List<Annotation> = emptyList(),
    /** Other apps' annotations, without the ones the user deleted. */
    val foreign: List<ForeignAnnotation> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val saveError: SaveError? = null,
)

/**
 * The annotations of one open document: edits with undo and redo, each
 * written to the draft in order, and saves requested from it.
 *
 * [scope] should outlive the screen (the application scope), so the last
 * writes and the save request still happen after it closes; [close] stops
 * watching the draft.
 *
 * @param onForeignHidden hides or shows a foreign annotation in the reader's
 *   document, since PDFium draws those.
 */
class AnnotationController(
    private val uri: String,
    private val repository: AnnotationRepository,
    private val scheduler: AnnotationSaveScheduler,
    private val scope: CoroutineScope,
    private val onForeignHidden: suspend (ForeignRef, hidden: Boolean) -> Unit,
) {
    private val _state = MutableStateFlow(AnnotationsUiState())
    val state: StateFlow<AnnotationsUiState> = _state.asStateFlow()

    private val _fileSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Emits when a save wrote a new version of the file. The reader's
     * document is then the old version, and must be reopened.
     */
    val fileSaved: Flow<Unit> = _fileSaved.asSharedFlow()

    private var history: AnnotationHistory? = null
    private var allForeign: List<ForeignAnnotation> = emptyList()
    private var base: FileStamp? = null
    private var watch: Job? = null

    /** Writes run one after another, in the order edits were made. */
    private val writes = Mutex()

    suspend fun prepare(document: PdfDocument) {
        val draft = repository.prepare(uri, document)
        base = draft.base
        history = AnnotationHistory(AnnotationState(draft.annotations, draft.removedForeign))
        draft.removedForeign.forEach { onForeignHidden(it, true) }
        allForeign = (0 until document.pageCount).flatMap { document.annotations(it).foreign }
        _state.update { it.copy(ready = true, saveError = draft.saveError) }
        publish()
        watch = scope.launch {
            repository.observe(uri).filterNotNull().collect { stored ->
                _state.update { it.copy(saveError = stored.saveError) }
                if (stored.base != base) {
                    base = stored.base
                    _fileSaved.tryEmit(Unit)
                }
            }
        }
    }

    fun add(annotation: Annotation) = change { it.add(annotation) }

    fun remove(id: String) = change { it.remove(id) }

    fun removeForeign(ref: ForeignRef) = change { it.removeForeign(ref) }

    /** Swaps [old] for [new] as one step: how a note is edited. */
    fun replace(old: String, new: Annotation) = change { it.replace(old, new) }

    /** Everything one eraser gesture removed, as one step. */
    fun removeAll(ids: Set<String>, refs: Set<ForeignRef>) = change { it.removeAll(ids, refs) }

    fun undo() = change { it.undo() }

    fun redo() = change { it.redo() }

    /** Asks for a background save once pending writes are done, if anything is unsaved. */
    fun requestSave() {
        scope.launch {
            writes.withLock {
                if (repository.observe(uri).first()?.dirty == true) scheduler.requestSave(uri)
            }
        }
    }

    fun close() {
        watch?.cancel()
    }

    private fun change(edit: (AnnotationHistory) -> AnnotationState) {
        val history = history ?: return
        val before = history.state
        val after = edit(history)
        if (after == before) return
        publish()
        scope.launch {
            (after.removedForeign - before.removedForeign).forEach { onForeignHidden(it, true) }
            (before.removedForeign - after.removedForeign).forEach { onForeignHidden(it, false) }
        }
        scope.launch {
            writes.withLock { repository.update(uri, after.annotations, after.removedForeign) }
        }
    }

    private fun publish() {
        val history = history ?: return
        val current = history.state
        _state.update {
            it.copy(
                own = current.annotations,
                foreign = allForeign.filterNot { foreign -> foreign.ref in current.removedForeign },
                canUndo = history.canUndo,
                canRedo = history.canRedo,
            )
        }
    }
}
