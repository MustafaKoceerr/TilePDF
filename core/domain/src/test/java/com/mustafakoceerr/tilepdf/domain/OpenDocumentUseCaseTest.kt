package com.mustafakoceerr.tilepdf.domain

import android.graphics.Bitmap
import android.graphics.Matrix
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import com.mustafakoceerr.tilepdf.model.PageText
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OpenDocumentUseCaseTest {

    private val document = ClosableDocument()

    @Test
    fun opensAndRestoresPosition() = runTest {
        val opened = OpenDocumentUseCase(Documents(), Library(position = ReadingPosition(pageIndex = 4)))("content://a.pdf")
        assertEquals("a.pdf", opened.displayName)
        assertEquals(4, opened.position.pageIndex)
        assertFalse(document.closed)
    }

    @Test
    fun documentIsClosedWhenRecordingFails() = runTest {
        val failure = runCatching {
            OpenDocumentUseCase(Documents(), Library(recordError = IOException("disk full")))("content://a.pdf")
        }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertTrue("document leaked", document.closed)
    }

    @Test
    fun documentIsClosedWhenCancelledAfterOpening() = runTest {
        val failure = runCatching {
            OpenDocumentUseCase(Documents(nameError = CancellationException("left")), Library())("content://a.pdf")
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue("document leaked", document.closed)
    }

    @Test
    fun opensThePreparedUri() = runTest {
        val documents = Documents(prepared = mapOf("content://x" to "file://copy.pdf"))
        val library = Library()
        val opened = OpenDocumentUseCase(documents, library)("content://x")
        assertEquals("file://copy.pdf", opened.uri)
        assertEquals(listOf("file://copy.pdf"), documents.opened)
        assertEquals(listOf("file://copy.pdf"), library.recorded)
    }

    @Test
    fun passesThePassword() = runTest {
        val documents = Documents()
        OpenDocumentUseCase(documents, Library())("content://x", password = "secret")
        assertEquals(listOf("secret"), documents.passwords)
    }

    @Test
    fun reopenedCopyKeepsItsLibraryName() = runTest {
        // A private copy's file name is a stand-in; the library has the real name.
        val documents = Documents(names = mapOf("file://copy.pdf" to null))
        val library = Library(names = mapOf("file://copy.pdf" to "Sözleşme.pdf"))
        val opened = OpenDocumentUseCase(documents, library)("file://copy.pdf")
        assertEquals("Sözleşme.pdf", opened.displayName)
        assertEquals(listOf("Sözleşme.pdf"), library.recordedNames)
    }

    @Test
    fun cleanUpKeepsOnlyLibraryCopies() = runTest {
        val documents = Documents()
        val library = Library(documents = listOf("file://a.pdf", "content://b"))
        CleanUpCopiesUseCase(documents, library)()
        assertEquals(setOf("file://a.pdf", "content://b"), documents.kept)
    }

    private inner class Documents(
        private val nameError: Throwable? = null,
        private val prepared: Map<String, String> = emptyMap(),
        private val names: Map<String, String?> = emptyMap(),
    ) : DocumentRepository {
        var kept: Set<String>? = null
        val opened = mutableListOf<String>()
        val passwords = mutableListOf<String?>()
        override fun persistAccess(uri: String) = Unit
        override suspend fun accessibleUris(): Set<String> = emptySet()
        override suspend fun displayName(uri: String): String? =
            nameError?.let { throw it } ?: if (uri in names) names[uri] else "a.pdf"
        override suspend fun prepare(uri: String): String = prepared[uri] ?: uri
        override suspend fun forget(uri: String) = Unit
        override suspend fun forgetCopiesExcept(keep: Set<String>) {
            kept = keep
        }
        override suspend fun open(uri: String, password: String?): PdfDocument {
            opened += uri
            passwords += password
            return document
        }
    }

    private class Library(
        private val position: ReadingPosition? = null,
        private val recordError: Throwable? = null,
        private val names: Map<String, String> = emptyMap(),
        private val documents: List<String> = emptyList(),
    ) : LibraryRepository {
        val recorded = mutableListOf<String>()
        val recordedNames = mutableListOf<String>()
        override fun observeDocuments(): Flow<List<LibraryDocument>> =
            flowOf(documents.map { LibraryDocument(it, "x.pdf", 1, ReadingPosition(), 0L) })
        override suspend fun displayName(uri: String): String? = names[uri]
        override suspend fun recordOpened(uri: String, displayName: String, pageCount: Int) {
            recordError?.let { throw it }
            recorded += uri
            recordedNames += displayName
        }
        override suspend fun readingPosition(uri: String): ReadingPosition? = position
        override suspend fun saveReadingPosition(uri: String, position: ReadingPosition) = Unit
        override suspend fun setFavorite(uri: String, favorite: Boolean) = Unit
        override suspend fun remove(uri: String) = Unit
    }

    private class ClosableDocument : PdfDocument {
        var closed = false
        override val pageSizes = listOf(PageSize(595, 842))
        override suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) = error("unused")
        override suspend fun pageText(pageIndex: Int): PageText = error("unused")
        override suspend fun outline(): List<OutlineEntry> = error("unused")
        override suspend fun trimMemory() = Unit
        override suspend fun linkAt(pageIndex: Int, x: Float, y: Float): com.mustafakoceerr.tilepdf.core.pdf.PdfLink? = null
        override suspend fun annotations(pageIndex: Int) =
            com.mustafakoceerr.tilepdf.model.PageAnnotations(emptyList(), emptyList())
        override suspend fun setForeignHidden(pageIndex: Int, key: String, hidden: Boolean) = Unit
        override fun close() {
            closed = true
        }
    }
}
