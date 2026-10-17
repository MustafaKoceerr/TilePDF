package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaving
import com.mustafakoceerr.tilepdf.data.annotation.SaveResult
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SaveAsCopyUseCaseTest {

    private val events = mutableListOf<String>()
    private var result: SaveResult = SaveResult.Saved("content://copy")

    private val saver = object : AnnotationSaving {
        override suspend fun save(uri: String): SaveResult = error("unused")
        override suspend fun saveCopy(uri: String, target: String): SaveResult {
            events += "saveCopy $uri -> $target"
            return result
        }
    }

    private val library = object : LibraryRepository {
        override fun observeDocuments(): Flow<List<LibraryDocument>> = error("unused")
        override suspend fun displayName(uri: String): String? = error("unused")
        override suspend fun recordOpened(uri: String, displayName: String, pageCount: Int) {
            events += "record $uri $displayName $pageCount"
        }
        override suspend fun readingPosition(uri: String): ReadingPosition? = error("unused")
        override suspend fun saveReadingPosition(uri: String, position: ReadingPosition) = error("unused")
        override suspend fun remove(uri: String) {
            events += "remove $uri"
        }
    }

    private val documents = object : DocumentRepository {
        override fun persistAccess(uri: String) {
            events += "persist $uri"
        }
        override suspend fun prepare(uri: String): String = error("unused")
        override suspend fun forget(uri: String) = error("unused")
        override suspend fun forgetCopiesExcept(keep: Set<String>) = error("unused")
        override suspend fun accessibleUris(): Set<String> = error("unused")
        override suspend fun displayName(uri: String): String? = error("unused")
        override suspend fun open(uri: String, password: String?): PdfDocument = error("unused")
    }

    private val useCase = SaveAsCopyUseCase(saver, library, documents)

    @Test
    fun recordsTargetBeforeSaving() = runTest {
        useCase("content://book", "content://copy", "Kopya.pdf", pageCount = 12)
        assertEquals(
            listOf(
                "persist content://copy",
                "record content://copy Kopya.pdf 12",
                "saveCopy content://book -> content://copy",
            ),
            events,
        )
    }

    @Test
    fun successReturnsSaved() = runTest {
        assertEquals(SaveResult.Saved("content://copy"), useCase("content://book", "content://copy", "Kopya.pdf", 12))
    }

    @Test
    fun failureRemovesTarget() = runTest {
        result = SaveResult.Failed(SaveError.NoWritePermission)

        assertEquals(result, useCase("content://book", "content://copy", "Kopya.pdf", 12))

        assertEquals("remove content://copy", events.last())
    }
}
