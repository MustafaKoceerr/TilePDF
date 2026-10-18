package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.LibraryPreferencesRepository
import com.mustafakoceerr.tilepdf.data.thumbnail.ThumbnailStore
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.LibrarySort
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val thumbnails by lazy { ThumbnailStore(tmp.root, Dispatchers.Unconfined) }
    private val documents = Documents()
    private val library = Library(
        listOf(
            doc("Sözleşme.pdf", opened = 3),
            doc("Kalem.pdf", opened = 2, favorite = true),
            doc("file://copy.pdf", opened = 1),
        ),
    )
    private val preferences = Preferences()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun doc(name: String, opened: Long, favorite: Boolean = false) =
        LibraryDocument(if (name.startsWith("file://")) name else "content://$name", name, 10, ReadingPosition(), opened, opened, favorite)

    private fun TestScope.viewModel() =
        LibraryViewModel(library, documents, preferences, thumbnails, CoroutineScope(UnconfinedTestDispatcher(testScheduler)))

    /** Keeps the state flow collected, as the screen does. */
    private fun TestScope.collected(viewModel: LibraryViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    private suspend fun LibraryViewModel.success() = uiState.first { it is LibraryUiState.Success } as LibraryUiState.Success

    private fun LibraryUiState.Success.names() = items.map { it.document.displayName }

    @Test
    fun searchFilters() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onSearchOpen()
        viewModel.onQueryChange("sozles")
        val state = viewModel.success()
        assertTrue(state.searching)
        assertEquals(listOf("Sözleşme.pdf"), state.names())
    }

    @Test
    fun closingSearchClearsQuery() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onSearchOpen()
        viewModel.onQueryChange("kalem")
        viewModel.onSearchClose()
        val state = viewModel.success()
        assertEquals("", state.query)
        assertEquals(3, state.items.size)
    }

    @Test
    fun favoritesFilter() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onFilterSelected(LibraryFilter.Favorites)
        assertEquals(listOf("Kalem.pdf"), viewModel.success().names())
    }

    @Test
    fun sortIsSavedAndApplied() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onSortSelected(LibrarySort.Name)
        assertEquals(LibrarySort.Name, preferences.sort.value)
        assertEquals(listOf("file://copy.pdf", "Kalem.pdf", "Sözleşme.pdf"), viewModel.success().names())
    }

    @Test
    fun toggleFavoriteWrites() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onToggleFavorite("content://Sözleşme.pdf")
        assertTrue(library.documents.value.first { it.displayName == "Sözleşme.pdf" }.favorite)
        viewModel.onToggleFavorite("content://Sözleşme.pdf")
        assertEquals(false, library.documents.value.first { it.displayName == "Sözleşme.pdf" }.favorite)
    }

    @Test
    fun removeHidesUntilConfirmed() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onRemove("content://Kalem.pdf")
        val state = viewModel.success()
        assertEquals(listOf("Sözleşme.pdf", "file://copy.pdf"), state.names())
        assertEquals("content://Kalem.pdf", state.pendingRemoval)
        assertTrue(library.removed.isEmpty())
    }

    @Test
    fun undoRestores() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onRemove("content://Kalem.pdf")
        viewModel.onUndoRemove()
        val state = viewModel.success()
        assertEquals(3, state.items.size)
        assertNull(state.pendingRemoval)
        assertTrue(library.removed.isEmpty())
    }

    @Test
    fun timeoutCommits() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onRemove("content://Kalem.pdf")
        viewModel.onRemoveConfirmed()
        assertEquals(listOf("content://Kalem.pdf"), library.removed)
        assertNull(viewModel.success().pendingRemoval)
        // Too late to undo.
        viewModel.onUndoRemove()
        assertEquals(listOf("Sözleşme.pdf", "file://copy.pdf"), viewModel.success().names())
    }

    @Test
    fun secondRemovalCommitsFirst() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onRemove("content://Kalem.pdf")
        viewModel.onRemove("content://Sözleşme.pdf")
        assertEquals(listOf("content://Kalem.pdf"), library.removed)
        assertEquals("content://Sözleşme.pdf", viewModel.success().pendingRemoval)
    }

    @Test
    fun clearedCommitsPending() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onRemove("content://Kalem.pdf")
        viewModel.commitPendingRemoval()
        assertEquals(listOf("content://Kalem.pdf"), library.removed)
    }

    @Test
    fun removingDeletesThumbnail() = runTest {
        thumbnails.save("content://Kalem.pdf", byteArrayOf(1))
        val viewModel = viewModel().also { collected(it) }
        viewModel.onRemove("content://Kalem.pdf")
        viewModel.onRemoveConfirmed()
        assertEquals(emptySet<String>(), thumbnails.find(listOf("content://Kalem.pdf")).keys)
    }

    @Test
    fun itemsCarryTheirThumbnail() = runTest {
        thumbnails.save("content://Kalem.pdf", byteArrayOf(1))
        val viewModel = viewModel().also { collected(it) }
        val items = viewModel.success().items.associate { it.document.displayName to it.thumbnail }
        assertEquals(thumbnails.find(listOf("content://Kalem.pdf"))["content://Kalem.pdf"], items["Kalem.pdf"])
        assertNull(items["Sözleşme.pdf"])
    }

    @Test
    fun removingDeletesPrivateCopy() = runTest {
        val viewModel = viewModel().also { collected(it) }
        viewModel.onRemove("file://copy.pdf")
        viewModel.onRemoveConfirmed()
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

    private class Library(initial: List<LibraryDocument>) : LibraryRepository {
        val documents = MutableStateFlow(initial)
        val removed = mutableListOf<String>()
        override fun observeDocuments(): Flow<List<LibraryDocument>> = documents
        override suspend fun displayName(uri: String): String? = null
        override suspend fun recordOpened(uri: String, displayName: String, pageCount: Int) = Unit
        override suspend fun readingPosition(uri: String): ReadingPosition? = null
        override suspend fun saveReadingPosition(uri: String, position: ReadingPosition) = Unit
        override suspend fun setFavorite(uri: String, favorite: Boolean) {
            documents.update { all -> all.map { if (it.uri == uri) it.copy(favorite = favorite) else it } }
        }
        override suspend fun remove(uri: String) {
            removed += uri
            documents.update { all -> all.filterNot { it.uri == uri } }
        }
    }

    private class Preferences : LibraryPreferencesRepository {
        override val sort = MutableStateFlow(LibrarySort.Recent)
        override suspend fun setSort(sort: LibrarySort) {
            this.sort.value = sort
        }
    }
}
