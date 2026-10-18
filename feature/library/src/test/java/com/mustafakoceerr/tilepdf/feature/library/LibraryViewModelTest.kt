package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val documents = Documents()
    private val library = Library()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun removingDeletesPrivateCopy() = runTest {
        LibraryViewModel(library, documents).onRemove("file://copy.pdf")
        assertEquals(listOf("file://copy.pdf"), library.removed)
        assertEquals(listOf("file://copy.pdf"), documents.forgotten)
    }

    private class Documents : DocumentRepository {
        val forgotten = mutableListOf<String>()
        override fun persistAccess(uri: String) = Unit
        override suspend fun accessibleUris(): Set<String> = emptySet()
        override suspend fun displayName(uri: String): String? = null
        override suspend fun prepare(uri: String): String = uri
        override suspend fun forget(uri: String) {
            forgotten += uri
        }
        override suspend fun forgetCopiesExcept(keep: Set<String>) = Unit
        override suspend fun open(uri: String, password: String?): PdfDocument = error("unused")
    }

    private class Library : LibraryRepository {
        val removed = mutableListOf<String>()
        override fun observeDocuments(): Flow<List<LibraryDocument>> = emptyFlow()
        override suspend fun displayName(uri: String): String? = null
        override suspend fun recordOpened(uri: String, displayName: String, pageCount: Int) = Unit
        override suspend fun readingPosition(uri: String): ReadingPosition? = null
        override suspend fun saveReadingPosition(uri: String, position: ReadingPosition) = Unit
        override suspend fun remove(uri: String) {
            removed += uri
        }
    }
}
