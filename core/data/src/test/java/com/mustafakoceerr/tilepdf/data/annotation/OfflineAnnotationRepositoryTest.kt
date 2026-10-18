package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.data.text.FakePdfDocument
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.PageAnnotations
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineAnnotationRepositoryTest {

    private val uri = "content://book"
    private val dao = FakeAnnotationDraftDao()
    private var stamp: FileStamp? = FileStamp(100, 5)
    private val repository = OfflineAnnotationRepository(dao, stamps = { stamp }, now = { 1L })
    private val document = FakePdfDocument(List(3) { "" })

    private fun note(id: String, page: Int = 0): Annotation = Note(id, page, 0xFFFFEB3B.toInt(), PdfPoint(1f, 1f), "")

    private suspend fun stored() = repository.observe(uri).first()!!

    @Test
    fun prepareScansWhenNoDraft() = runTest {
        document.annotationsByPage = mapOf(
            0 to PageAnnotations(listOf(note("tilepdf-a")), emptyList()),
            2 to PageAnnotations(listOf(note("tilepdf-b", page = 2)), emptyList()),
        )

        val draft = repository.prepare(uri, document)

        assertEquals(listOf("tilepdf-a", "tilepdf-b"), draft.annotations.map { it.id })
        assertFalse(draft.dirty)
        assertEquals(FileStamp(100, 5), draft.base)
        assertEquals(draft, stored())
    }

    @Test
    fun prepareReturnsStoredDraft() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, listOf(note("tilepdf-x")), emptySet())
        val calls = document.annotationCalls

        val draft = repository.prepare(uri, document)

        assertEquals(calls, document.annotationCalls)
        assertEquals(listOf("tilepdf-x"), draft.annotations.map { it.id })
    }

    @Test
    fun cleanDraftOfChangedFileIsRescanned() = runTest {
        repository.prepare(uri, document)
        stamp = FileStamp(200, 9)
        document.annotationsByPage = mapOf(1 to PageAnnotations(listOf(note("tilepdf-new", 1)), emptyList()))

        val draft = repository.prepare(uri, document)

        assertEquals(listOf("tilepdf-new"), draft.annotations.map { it.id })
        assertEquals(FileStamp(200, 9), draft.base)
    }

    @Test
    fun dirtyDraftOfChangedFileIsMarkedChanged() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, listOf(note("tilepdf-x")), emptySet())
        stamp = FileStamp(200, 9)

        val draft = repository.prepare(uri, document)

        assertEquals(SaveError.FileChanged, draft.saveError)
        assertTrue(draft.dirty)
        assertEquals(listOf("tilepdf-x"), draft.annotations.map { it.id })
        assertEquals(FileStamp(100, 5), draft.base)
    }

    @Test
    fun updateMarksDirty() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, listOf(note("tilepdf-x")), setOf(ForeignRef(0, "#2")))
        val draft = stored()
        assertTrue(draft.dirty)
        assertEquals(setOf(ForeignRef(0, "#2")), draft.removedForeign)
    }

    @Test
    fun markSavedClearsDirty() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, listOf(note("tilepdf-x")), emptySet())
        repository.markFailed(uri, SaveError.Failed)
        val saved = stored()

        repository.markSaved(uri, saved, FileStamp(150, 7))

        val draft = stored()
        assertFalse(draft.dirty)
        assertEquals(FileStamp(150, 7), draft.base)
        assertNull(draft.saveError)
    }

    @Test
    fun markSavedKeepsLaterEditsDirty() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, listOf(note("tilepdf-x")), emptySet())
        val saved = stored()
        repository.update(uri, listOf(note("tilepdf-x"), note("tilepdf-y")), emptySet())

        repository.markSaved(uri, saved, FileStamp(150, 7))

        val draft = stored()
        assertTrue(draft.dirty)
        assertEquals(listOf("tilepdf-x", "tilepdf-y"), draft.annotations.map { it.id })
        assertEquals(FileStamp(150, 7), draft.base)
    }

    @Test
    fun markSavedDropsSavedAndIndexRefs() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, emptyList(), setOf(ForeignRef(0, "acrobat-a"), ForeignRef(0, "#3")))
        val saved = stored()
        repository.update(uri, emptyList(), setOf(ForeignRef(0, "acrobat-a"), ForeignRef(0, "#3"), ForeignRef(1, "acrobat-b"), ForeignRef(1, "#5")))

        repository.markSaved(uri, saved, FileStamp(150, 7))

        assertEquals(setOf(ForeignRef(1, "acrobat-b")), stored().removedForeign)
    }

    @Test
    fun markFailedKeepsDirty() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, listOf(note("tilepdf-x")), emptySet())
        repository.markFailed(uri, SaveError.NoWritePermission)
        val draft = stored()
        assertTrue(draft.dirty)
        assertEquals(SaveError.NoWritePermission, draft.saveError)
    }

    @Test
    fun moveToRekeysDraft() = runTest {
        repository.prepare(uri, document)
        repository.update(uri, listOf(note("tilepdf-x")), emptySet())
        val saved = stored()

        repository.moveTo(uri, "content://copy", saved, FileStamp(300, 1))

        assertNull(repository.observe(uri).first())
        val moved = repository.observe("content://copy").first()!!
        assertEquals(listOf("tilepdf-x"), moved.annotations.map { it.id })
        assertFalse(moved.dirty)
        assertEquals(FileStamp(300, 1), moved.base)
    }

    @Test
    fun dirtyUrisOnlyDirty() = runTest {
        repository.prepare(uri, document)
        repository.prepare("content://other", document)
        repository.update(uri, listOf(note("tilepdf-x")), emptySet())
        assertEquals(listOf(uri), repository.dirtyUris())
    }
}
