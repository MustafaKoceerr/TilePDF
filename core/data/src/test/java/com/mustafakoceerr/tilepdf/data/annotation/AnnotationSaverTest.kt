package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.model.XfaEdit
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.data.text.FakePdfDocument
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.FileStamp
import com.mustafakoceerr.tilepdf.model.ForeignRef
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.OcrWord
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
    private val repository by lazy { OfflineAnnotationRepository(FakeAnnotationDraftDao(), FakeRecognizedTextDao(), files, now = { 1L }) }
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

    @Test
    fun exportWritesDraftWithoutMovingIt() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        val target = tmp.newFile("shared.pdf")

        assertTrue(saver.export(uri, target))

        assertEquals(emptyList<String>(), files.replaced)
        val exported = files.openForReading(target, null).annotations(0).own.map { it.id }
        assertEquals(listOf("tilepdf-a"), exported)
        assertTrue(draft()!!.dirty)
    }

    @Test
    fun exportWithoutDraftCopiesDocument() = runTest {
        files.pdfs[uri] = FakePdf()
        val target = tmp.newFile("shared.pdf")

        assertTrue(saver.export(uri, target))

        assertTrue(target.length() > 0)
    }

    @Test
    fun failedExportLeavesNoFile() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        files.loseOnSave = "tilepdf-a"
        val target = tmp.newFile("shared.pdf")

        assertFalse(saver.export(uri, target))

        assertFalse(target.exists())
    }

    @Test
    fun exportNeedsPassword() = runTest {
        givenDraft(listOf(note("tilepdf-a")))
        files.passwords[uri] = "pw"
        val target = tmp.newFile("shared.pdf")

        assertFalse(saver.export(uri, target))

        passwords.remember(uri, "pw")
        assertTrue(saver.export(uri, target))
    }

    @Test
    fun savesFormValues() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(form = mapOf("name" to "", "city" to "")))
        repository.setFormValue(uri, "name", "Ada")

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(mapOf("name" to "Ada", "city" to ""), files.pdfs.getValue(uri).form)
        val draft = draft()!!
        assertFalse(draft.dirty)
        assertEquals(emptyMap<String, String>(), draft.formValues)
    }

    @Test
    fun matchingFormValuesNeedNoWrite() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(form = mapOf("name" to "Ada")))
        repository.setFormValue(uri, "name", "Ada")

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(emptyList<String>(), files.replaced)
        assertFalse(draft()!!.dirty)
    }

    @Test
    fun lostFormValueFailsVerification() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(form = mapOf("name" to "")))
        repository.setFormValue(uri, "name", "Ada")
        files.loseFormOnSave = "name"

        assertEquals(SaveResult.Failed(SaveError.Failed), saver.save(uri))

        assertEquals(emptyList<String>(), files.replaced)
        assertEquals(mapOf("name" to "Ada"), draft()!!.formValues)
    }

    @Test
    fun exportIncludesFormValues() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(form = mapOf("name" to "")))
        repository.setFormValue(uri, "name", "Ada")
        val target = tmp.newFile("shared.pdf")

        assertTrue(saver.export(uri, target))

        assertEquals(mapOf("name" to "Ada"), files.openForReading(target, null).let { (it as FakePdfDocument).form })
    }

    private fun words(text: String) =
        listOf(OcrWord(text, listOf(PdfPoint(0f, 0f), PdfPoint(9f, 0f), PdfPoint(9f, 4f), PdfPoint(0f, 4f))))

    @Test
    fun savesRecognizedText() = runTest {
        givenDraft(emptyList())
        repository.setRecognizedText(uri, 1, words("taranmış"))

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(mapOf(1 to words("taranmış")), files.pdfs.getValue(uri).recognized)
        assertFalse(draft()!!.dirty)
        assertEquals(emptySet<Int>(), draft()!!.recognizedPages)
    }

    @Test
    fun pageTheFileAlreadyRecognizedIsNotWrittenAgain() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(recognized = mapOf(1 to words("eski"))))
        repository.setRecognizedText(uri, 1, words("yeni"))

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(emptyList<Int>(), files.recognizedAdds)
        assertEquals(mapOf(1 to words("eski")), files.pdfs.getValue(uri).recognized)
        assertEquals(emptySet<Int>(), draft()!!.recognizedPages)
    }

    @Test
    fun lostRecognizedTextFailsTheSave() = runTest {
        givenDraft(emptyList())
        repository.setRecognizedText(uri, 0, words("bir"))
        files.loseRecognizedOnSave = true

        assertEquals(SaveResult.Failed(SaveError.Failed), saver.save(uri))

        assertEquals(emptyList<String>(), files.replaced)
        assertEquals(setOf(0), draft()!!.recognizedPages)
    }

    @Test
    fun exportIncludesRecognizedText() = runTest {
        givenDraft(emptyList())
        repository.setRecognizedText(uri, 2, words("üç"))
        val target = tmp.newFile("shared.pdf")

        assertTrue(saver.export(uri, target))

        assertTrue(files.openForReading(target, null).hasRecognizedText(2))
        assertEquals(setOf(2), draft()!!.recognizedPages)
    }

    private val nameField = PdfPoint(200f, 122f)

    @Test
    fun savesXfaEdits() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(xfa = mapOf(nameField to "")))
        repository.addXfaEdit(uri, XfaEdit(0, nameField, "Ada"))

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(mapOf(nameField to "Ada"), files.pdfs.getValue(uri).xfa)
        assertEquals(emptyList<XfaEdit>(), draft()!!.xfaEdits)
        assertFalse(draft()!!.dirty)
    }

    @Test
    fun lostXfaTextFailsTheSave() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(xfa = mapOf(nameField to "")))
        repository.addXfaEdit(uri, XfaEdit(0, nameField, "Ada"))
        files.loseXfaOnSave = true

        assertEquals(SaveResult.Failed(SaveError.Failed), saver.save(uri))

        assertEquals(emptyList<String>(), files.replaced)
    }

    @Test
    fun theLastTextAtAPointIsWhatIsChecked() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(xfa = mapOf(nameField to "")))
        repository.addXfaEdit(uri, XfaEdit(0, nameField, "Ada"))
        repository.addXfaEdit(uri, XfaEdit(0, nameField, "Grace"))

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals("Grace", files.pdfs.getValue(uri).xfa[nameField])
    }

    @Test
    fun exportReplaysXfaEdits() = runTest {
        givenDraft(emptyList(), pdf = FakePdf(xfa = mapOf(nameField to "")))
        repository.addXfaEdit(uri, XfaEdit(0, nameField, "Ada"))
        val target = tmp.newFile("shared.pdf")

        assertTrue(saver.export(uri, target))

        assertEquals("Ada", files.openForReading(target, null).xfaTap(0, nameField))
    }
}
