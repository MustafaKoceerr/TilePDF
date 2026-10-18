package com.mustafakoceerr.tilepdf.data.annotation

import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.core.pdf.Pdfium
import com.mustafakoceerr.tilepdf.data.local.TilePdfDatabase
import com.mustafakoceerr.tilepdf.model.Note
import com.mustafakoceerr.tilepdf.model.newAnnotationId
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith

/** The whole save on real files: draft in Room, PDFium, temp file, overwrite. */
@RunWith(AndroidJUnit4::class)
class AnnotationSaveFlowTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, Dispatchers.IO.limitedParallelism(1))
    private val database = Room.inMemoryDatabaseBuilder(context, TilePdfDatabase::class.java).build()
    private val files = SafAnnotationFiles(context, Dispatchers.IO, pdfium)
    private val repository = OfflineAnnotationRepository(database.annotationDraftDao(), database.recognizedTextDao(), files)
    private val passwords = SessionPasswords()
    private val saver = AnnotationSaver(repository, files, passwords)

    @After
    fun tearDown() = database.close()

    private val note = Note(newAnnotationId(), 0, 0xFFFFEB3B.toInt(), PdfPoint(100f, 100f), "Kaydedildi")

    /** Puts [asset] in the library with a draft that adds [note]; returns its URI. */
    private fun annotated(asset: String, password: String? = null): String = runBlocking {
        val uri = TestAssets.uriString(asset)
        database.documentDao().recordOpened(uri, asset, pageCount = 1, now = 1)
        pdfium.open(uri.toUri(), password).use { repository.prepare(uri, it) }
        repository.update(uri, listOf(note), emptySet())
        uri
    }

    private fun savedNotes(uri: String, password: String? = null) = runBlocking {
        pdfium.open(uri.toUri(), password).use { document ->
            (0 until document.pageCount).flatMap { document.annotations(it).own }
        }
    }

    @Test
    fun savesIntoTheFile() = runBlocking {
        val uri = annotated("sample.pdf")

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(listOf(note), savedNotes(uri))
        assertFalse(repository.observe(uri).first()!!.dirty)
    }

    @Test
    fun encryptedDocumentSaves() = runBlocking {
        val uri = annotated("encrypted.pdf", password = "secret")
        passwords.remember(uri, "secret")

        assertEquals(SaveResult.Saved(uri), saver.save(uri))

        assertEquals(listOf(note.id), savedNotes(uri, password = "secret").map { it.id })
    }

    @Test
    fun stampChangesAfterSave() = runBlocking {
        val uri = annotated("sample.pdf")
        val before = files.stamp(uri)

        saver.save(uri)

        val after = files.stamp(uri)
        assertNotEquals(before, after)
        assertEquals(after, repository.observe(uri).first()!!.base)
    }
}
