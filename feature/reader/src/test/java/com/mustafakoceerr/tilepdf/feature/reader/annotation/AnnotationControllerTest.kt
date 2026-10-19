package com.mustafakoceerr.tilepdf.feature.reader.annotation

import android.graphics.Bitmap
import android.graphics.Matrix
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.core.pdf.PdfLink
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationRepository
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.OcrWord
import com.mustafakoceerr.tilepdf.model.XfaEdit
import com.mustafakoceerr.tilepdf.model.AnnotationDraft
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignAnnotation
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Ink
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.PageAnnotations
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnotationControllerTest {

    private val uri = "content://book"
    private val color = 0xFFFFEB3B.toInt()
    private val base = FileStamp(100, 5)

    private fun note(id: String, text: String = ""): Annotation = Note(id, 0, color, PdfPoint(1f, 1f), text)
    private fun ink(id: String): Annotation = Ink(id, 0, color, 2f, listOf(listOf(PdfPoint(1f, 1f))))
    private fun foreign(key: String, page: Int = 0) = ForeignAnnotation(page, key, 9, PdfRect(0f, 0f, 1f, 1f), "", emptyList())

    private val repository = FakeAnnotationRepository()
    private val requested = mutableListOf<String>()
    private val hidden = mutableListOf<Pair<ForeignRef, Boolean>>()
    private val document = FakeAnnotatedDocument(
        mapOf(0 to PageAnnotations(listOf(note("tilepdf-a")), listOf(foreign("acrobat-1"))), 2 to PageAnnotations(emptyList(), listOf(foreign("#2", 2)))),
    )

    /**
     * Runs the controller's work as soon as it is launched: advanceUntilIdle()
     * doesn't wait for backgroundScope, and these coroutines outlive the test.
     */
    private fun TestScope.controller() = AnnotationController(
        uri, repository, scheduler = { requested += it },
        scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
        onForeignHidden = { ref, hide -> hidden += ref to hide },
    )

    private suspend fun TestScope.prepared(): AnnotationController = controller().also {
        it.prepare(document)
        advanceUntilIdle()
    }

    private fun AnnotationController.ids() = state.value.own.map { it.id }

    @Test
    fun prepareMakesReadyWithDraftAndForeign() = runTest {
        val controller = controller()
        assertFalse(controller.state.value.ready)

        controller.prepare(document)
        advanceUntilIdle()

        val state = controller.state.value
        assertTrue(state.ready)
        assertEquals(listOf("tilepdf-a"), state.own.map { it.id })
        assertEquals(listOf("acrobat-1", "#2"), state.foreign.map { it.key })
    }

    @Test
    fun addUpdatesRepository() = runTest {
        val controller = prepared()
        controller.add(note("tilepdf-b"))
        advanceUntilIdle()

        assertEquals(listOf("tilepdf-a", "tilepdf-b"), controller.ids())
        assertEquals(listOf("tilepdf-a", "tilepdf-b"), repository.draft(uri).annotations.map { it.id })
        assertTrue(repository.draft(uri).dirty)
    }

    @Test
    fun undoRedoRoundTrip() = runTest {
        val controller = prepared()
        controller.add(note("tilepdf-b"))
        controller.undo()
        advanceUntilIdle()
        assertEquals(listOf("tilepdf-a"), controller.ids())
        assertTrue(controller.state.value.canRedo)
        assertEquals(listOf("tilepdf-a"), repository.draft(uri).annotations.map { it.id })

        controller.redo()
        advanceUntilIdle()
        assertEquals(listOf("tilepdf-a", "tilepdf-b"), controller.ids())
    }

    @Test
    fun undoShowsForeignAgain() = runTest {
        val controller = prepared()
        controller.removeForeign(ForeignRef(0, "acrobat-1"))
        advanceUntilIdle()
        controller.undo()
        advanceUntilIdle()

        assertEquals(listOf(ForeignRef(0, "acrobat-1") to true, ForeignRef(0, "acrobat-1") to false), hidden)
        assertEquals(listOf("acrobat-1", "#2"), controller.state.value.foreign.map { it.key })
    }

    @Test
    fun deletedForeignNotListed() = runTest {
        val controller = prepared()
        controller.removeForeign(ForeignRef(2, "#2"))
        assertEquals(listOf("acrobat-1"), controller.state.value.foreign.map { it.key })
        advanceUntilIdle()
        assertEquals(setOf(ForeignRef(2, "#2")), repository.draft(uri).removedForeign)
    }

    @Test
    fun eraseIsOneUndoStep() = runTest {
        val controller = prepared()
        controller.add(ink("tilepdf-1"))
        controller.add(ink("tilepdf-2"))
        controller.removeAll(setOf("tilepdf-1", "tilepdf-2"), setOf(ForeignRef(0, "acrobat-1")))
        controller.undo()
        advanceUntilIdle()

        assertEquals(listOf("tilepdf-a", "tilepdf-1", "tilepdf-2"), controller.ids())
        assertEquals(listOf("acrobat-1", "#2"), controller.state.value.foreign.map { it.key })
    }

    @Test
    fun replaceEditsNoteInOneStep() = runTest {
        val controller = prepared()
        controller.replace("tilepdf-a", note("tilepdf-a2", "yeni"))
        assertEquals(listOf("tilepdf-a2"), controller.ids())
        controller.undo()
        assertEquals(listOf("tilepdf-a"), controller.ids())
        assertFalse(controller.state.value.canUndo)
    }

    @Test
    fun previouslyRemovedForeignHiddenOnPrepare() = runTest {
        repository.drafts.value += uri to AnnotationDraft(uri, emptyList(), setOf(ForeignRef(0, "acrobat-1")), dirty = true, base = base, saveError = null)

        val controller = prepared()

        assertEquals(listOf(ForeignRef(0, "acrobat-1") to true), hidden)
        assertEquals(listOf("#2"), controller.state.value.foreign.map { it.key })
    }

    @Test
    fun requestSaveOnlyWhenDirty() = runTest {
        val controller = prepared()
        controller.requestSave()
        advanceUntilIdle()
        assertTrue(requested.isEmpty())

        controller.add(note("tilepdf-b"))
        controller.requestSave()
        advanceUntilIdle()
        assertEquals(listOf(uri), requested)
    }

    @Test
    fun saveErrorIsExposed() = runTest {
        val controller = prepared()
        repository.drafts.value += uri to repository.draft(uri).copy(saveError = SaveError.NoWritePermission)
        advanceUntilIdle()
        assertEquals(SaveError.NoWritePermission, controller.state.value.saveError)
    }

    @Test
    fun fileSavedIsReported() = runTest {
        val controller = prepared()
        val events = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.fileSaved.toList(events) }
        controller.add(note("tilepdf-b"))
        advanceUntilIdle()

        // A background save wrote a new version of the file.
        repository.drafts.value += uri to repository.draft(uri).copy(dirty = false, base = FileStamp(150, 9))
        advanceUntilIdle()
        // The same version again is not a new save.
        repository.drafts.value += uri to repository.draft(uri).copy(saveError = null)
        advanceUntilIdle()

        assertEquals(1, events.size)
    }
}

/** Drafts in memory; a new draft is scanned from the document, as the real one does. */
class FakeAnnotationRepository : AnnotationRepository {
    val drafts = MutableStateFlow<Map<String, AnnotationDraft>>(emptyMap())

    fun draft(uri: String) = drafts.value.getValue(uri)

    override fun observe(uri: String): Flow<AnnotationDraft?> = drafts.map { it[uri] }

    override suspend fun prepare(uri: String, document: PdfDocument): AnnotationDraft = drafts.value[uri]
        ?: AnnotationDraft(
            uri, (0 until document.pageCount).flatMap { document.annotations(it).own }, emptySet(),
            dirty = false, base = FileStamp(100, 5), saveError = null,
        ).also { drafts.value += uri to it }

    override suspend fun update(uri: String, annotations: List<Annotation>, removedForeign: Set<ForeignRef>) {
        drafts.value += uri to draft(uri).copy(annotations = annotations, removedForeign = removedForeign, dirty = true)
    }
    override suspend fun setFormValue(uri: String, name: String, value: String) = Unit

    override suspend fun addXfaEdit(uri: String, edit: XfaEdit) {
        drafts.value[uri]?.let { drafts.value += uri to it.copy(dirty = true, xfaEdits = it.xfaEdits + edit) }
    }

    /** Recognised words by uri and page, until saved. */
    val recognized = MutableStateFlow<Map<Pair<String, Int>, List<OcrWord>>>(emptyMap())

    override suspend fun setRecognizedText(uri: String, pageIndex: Int, words: List<OcrWord>) {
        recognized.value += (uri to pageIndex) to words
        drafts.value[uri]?.let { draft ->
            drafts.value += uri to draft.copy(dirty = true, recognizedPages = draft.recognizedPages + pageIndex)
        }
    }

    override suspend fun recognizedText(uri: String, pages: Set<Int>): Map<Int, List<OcrWord>> =
        recognized.value.filterKeys { (u, page) -> u == uri && page in pages }.mapKeys { it.key.second }

    override suspend fun markSaved(uri: String, saved: AnnotationDraft, newBase: FileStamp) = error("unused")
    override suspend fun markFailed(uri: String, error: SaveError) = error("unused")
    override suspend fun moveTo(from: String, to: String, saved: AnnotationDraft, newBase: FileStamp) = error("unused")
    override suspend fun dirtyUris(): List<String> = error("unused")
}

/** A 3-page document with the given annotations per page. */
class FakeAnnotatedDocument(private val pages: Map<Int, PageAnnotations>) : PdfDocument {
    override val pageSizes = List(3) { PageSize(595, 842) }
    override suspend fun annotations(pageIndex: Int) = pages[pageIndex] ?: PageAnnotations(emptyList(), emptyList())
    override suspend fun setForeignHidden(pageIndex: Int, key: String, hidden: Boolean) = Unit
    override suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) = error("unused")
    override suspend fun pageText(pageIndex: Int): PageText = error("unused")
    override suspend fun outline(): List<OutlineEntry> = error("unused")
    override suspend fun linkAt(pageIndex: Int, x: Float, y: Float): PdfLink? = error("unused")
    override suspend fun trimMemory() = Unit
    override fun close() = Unit
}
