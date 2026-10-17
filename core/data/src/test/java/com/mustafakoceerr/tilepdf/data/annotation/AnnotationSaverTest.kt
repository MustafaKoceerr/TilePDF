package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.data.text.FakePdfDocument
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AnnotationSaverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val uri = "content://book"
    private val copy = "content://copy"
    private val base = FileStamp(100, 5)

    private val files by lazy { FakeAnnotationFiles(tmp.root) }
    private val repository by lazy { OfflineAnnotationRepository(FakeAnnotationDraftDao(), files, now = { 1L }) }
    private val passwords = SessionPasswords()
    private val saver by lazy { AnnotationSaver(repository, files, passwords) }

    private fun note(id: String): Annotation = Note(id, 0, 0xFFFFEB3B.toInt(), PdfPoint(1f, 1f), "")

    /** [uri] holds [pdf]; its draft then wants [annotations] and [removed]. */
    private suspend fun givenDraft(
        annotations: List<Annotation>,
        removed: Set<ForeignRef> = emptySet(),
        pdf: FakePdf = FakePdf(),
    ) {
        files.pdfs[uri] = pdf
        files.stamps[uri] = base
        repository.prepare(uri, FakePdfDocument(List(3) { "" }))
        repository.update(uri, annotations, removed)
    }

    private suspend fun draft(of: String = uri) = repository.observe(of).first()

    @Test
    fun cleanDraftSavesNothing() = runTest {
        files.pdfs[uri] = FakePdf()
        files.stamps[uri] = base
        repository.prepare(uri, FakePdfDocument(List(3) { "" }))

        assertEquals(SaveResult.NothingToSave, saver.save(uri))
        assertEquals(0, files.editOpens)
    }

    @Test
    fun savesAndMarksSaved() = runTest {
        givenDraft(listOf(note("tilepdf-a")))

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(listOf(uri), files.replaced)
        assertEquals(listOf("tilepdf-a"), files.pdfs.getValue(uri).own.map { it.id })
        val draft = draft()!!
        assertFalse(draft.dirty)
        assertEquals(files.stamps[uri], draft.base)
    }

    @Test
    fun changedFileIsNotOverwritten() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        files.stamps[uri] = FileStamp(200, 6)

        assertEquals(SaveResult.Failed(SaveError.FileChanged), saver.save(uri))

        assertEquals(0, files.editOpens)
        assertEquals(SaveError.FileChanged, draft()!!.saveError)
    }

    @Test
    fun fileWithoutStampStillSaves() = runTest {
        // The provider can't be queried for size or date: changes can't be detected, but saving must work.
        files.pdfs[uri] = FakePdf()
        repository.prepare(uri, FakePdfDocument(List(3) { "" }))
        repository.update(uri, listOf(note("tilepdf-a")), emptySet())

        assertEquals(SaveResult.Saved(uri), saver.save(uri))
    }

    @Test
    fun noWriteAccessKeepsDraft() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        files.replaceFailure = SecurityException("read only")

        assertEquals(SaveResult.Failed(SaveError.NoWritePermission), saver.save(uri))

        val draft = draft()!!
        assertTrue(draft.dirty)
        assertEquals(SaveError.NoWritePermission, draft.saveError)
    }

    @Test
    fun verificationFailureKeepsOriginal() = runTest {
        givenDraft(listOf(note("tilepdf-a"), note("tilepdf-b")))
        files.loseOnSave = "tilepdf-b"

        assertEquals(SaveResult.Failed(SaveError.Failed), saver.save(uri))

        assertTrue(files.replaced.isEmpty())
        assertTrue(files.pdfs.getValue(uri).own.isEmpty())
        assertEquals(SaveError.Failed, draft()!!.saveError)
    }

    @Test
    fun missingPasswordIsRecorded() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        files.passwords[uri] = "secret"

        assertEquals(SaveResult.Failed(SaveError.NeedsPassword), saver.save(uri))
        assertEquals(SaveError.NeedsPassword, draft()!!.saveError)
    }

    @Test
    fun sessionPasswordIsUsed() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        files.passwords[uri] = "secret"
        passwords.remember(uri, "secret")

        assertEquals(SaveResult.Saved(uri), saver.save(uri))
    }

    @Test
    fun tempFileIsAlwaysDeleted() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        saver.save(uri)
        repository.update(uri, listOf(note("tilepdf-a"), note("tilepdf-b")), emptySet())
        files.replaceFailure = SecurityException("read only")
        saver.save(uri)

        assertEquals(2, files.temps.size)
        files.temps.forEach { assertFalse("$it still exists", it.exists()) }
    }

    @Test
    fun nothingChangedStillMarksSaved() = runTest {
        givenDraft(listOf(note("tilepdf-a")), pdf = FakePdf(own = listOf(note("tilepdf-a"))))

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertTrue(files.replaced.isEmpty())
        assertFalse(draft()!!.dirty)
    }

    @Test
    fun foreignRemovalsAreSaved() = runTest {
        givenDraft(emptyList(), removed = setOf(ForeignRef(0, "acrobat-1")), pdf = FakePdf(foreign = mapOf(0 to setOf("acrobat-1", "#2"))))

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(setOf("#2"), files.pdfs.getValue(uri).foreign[0])
        assertEquals(emptySet<ForeignRef>(), draft()!!.removedForeign)
    }

    @Test
    fun copyMovesDraft() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        passwords.remember(uri, "pw")

        assertEquals(SaveResult.Saved(copy), saver.saveCopy(uri, copy))

        assertEquals(listOf(copy), files.replaced)
        assertNull(draft(uri))
        val moved = draft(copy)!!
        assertFalse(moved.dirty)
        assertEquals(listOf("tilepdf-a"), moved.annotations.map { it.id })
        assertEquals("pw", passwords.get(copy))
    }

    @Test
    fun copyWithChangedFileSkipsIndexRefs() = runTest {
        givenDraft(
            emptyList(),
            removed = setOf(ForeignRef(0, "#2"), ForeignRef(0, "acrobat-1")),
            pdf = FakePdf(foreign = mapOf(0 to setOf("acrobat-1", "#2"))),
        )
        files.stamps[uri] = FileStamp(200, 6)

        assertEquals(SaveResult.Saved(copy), saver.saveCopy(uri, copy))

        assertEquals(mapOf(0 to setOf("acrobat-1")), files.applied.single().foreignRemovals)
    }

    @Test
    fun failedCopyLeavesDraftUnmarked() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        files.replaceFailure = SecurityException("no")

        assertEquals(SaveResult.Failed(SaveError.NoWritePermission), saver.saveCopy(uri, copy))

        assertNull(draft(uri)!!.saveError)
        assertNull(draft(copy))
    }
}
