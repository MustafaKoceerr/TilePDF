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
import kotlinx.coroutines.flow.emptyFlow
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

    private inner class Documents(private val nameError: Throwable? = null) : DocumentRepository {
        override fun persistAccess(uri: String) = Unit
        override suspend fun accessibleUris(): Set<String> = emptySet()
        override suspend fun displayName(uri: String): String? = nameError?.let { throw it } ?: "a.pdf"
        override suspend fun open(uri: String, password: String?): PdfDocument = document
    }

    private class Library(
        private val position: ReadingPosition? = null,
        private val recordError: Throwable? = null,
    ) : LibraryRepository {
        override fun observeDocuments(): Flow<List<LibraryDocument>> = emptyFlow()
        override suspend fun recordOpened(uri: String, displayName: String, pageCount: Int) {
            recordError?.let { throw it }
        }
        override suspend fun readingPosition(uri: String): ReadingPosition? = position
        override suspend fun saveReadingPosition(uri: String, position: ReadingPosition) = Unit
        override suspend fun remove(uri: String) = Unit
    }

    private class ClosableDocument : PdfDocument {
        var closed = false
        override val pageSizes = listOf(PageSize(595, 842))
        override suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) = error("unused")
        override suspend fun pageText(pageIndex: Int): PageText = error("unused")
        override suspend fun outline(): List<OutlineEntry> = error("unused")
        override suspend fun trimMemory() = Unit
        override fun close() {
            closed = true
        }
    }
}
