package com.mustafakoceerr.tilepdf.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.LibraryPreferencesRepository
import com.mustafakoceerr.tilepdf.data.thumbnail.ThumbnailStore
import com.mustafakoceerr.tilepdf.di.ApplicationScope
import com.mustafakoceerr.tilepdf.model.LibrarySort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val documentRepository: DocumentRepository,
    private val libraryPreferences: LibraryPreferencesRepository,
    private val thumbnails: ThumbnailStore,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val searching = MutableStateFlow(false)
    private val filter = MutableStateFlow(LibraryFilter.All)

    /** The document removed last, hidden until its undo window ends. */
    private val pendingRemoval = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeDocuments(),
        libraryPreferences.sort,
        combine(query, searching, filter, ::Triple),
        pendingRemoval,
    ) { documents, sort, (query, searching, filter), pending ->
        val accessible = documentRepository.accessibleUris()
        val rows = libraryRows(documents, accessible, query, filter, sort, hidden = setOfNotNull(pending))
        val pictures = thumbnails.find(rows.map { it.document.uri })
        LibraryUiState.Success(
            items = rows.map { it.copy(thumbnail = pictures[it.document.uri]) },
            query = query,
            searching = searching,
            filter = filter,
            sort = sort,
            hasDocuments = documents.any { it.uri != pending },
            pendingRemoval = pending,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState.Loading,
    )

    /** A file came back from the system picker. */
    fun onDocumentPicked(uri: String) {
        documentRepository.persistAccess(uri)
    }

    fun onSearchOpen() {
        searching.value = true
    }

    fun onSearchClose() {
        searching.value = false
        query.value = ""
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onFilterSelected(selected: LibraryFilter) {
        filter.value = selected
    }

    fun onSortSelected(sort: LibrarySort) {
        viewModelScope.launch { libraryPreferences.setSort(sort) }
    }

    fun onToggleFavorite(uri: String) {
        val current = (uiState.value as? LibraryUiState.Success)?.items?.firstOrNull { it.document.uri == uri } ?: return
        viewModelScope.launch { libraryRepository.setFavorite(uri, !current.document.favorite) }
    }

    /** Hides [uri] at once; it's removed when the undo window ends. An earlier removal is done now. */
    fun onRemove(uri: String) {
        commitPendingRemoval()
        pendingRemoval.value = uri
    }

    fun onUndoRemove() {
        pendingRemoval.value = null
    }

    /** The undo window ended without Undo. */
    fun onRemoveConfirmed() = commitPendingRemoval()

    /**
     * Removes the pending document now. Runs on the application scope, so
     * leaving the screen mid-window still removes it.
     */
    internal fun commitPendingRemoval() {
        val uri = pendingRemoval.value ?: return
        applicationScope.launch {
            libraryRepository.remove(uri)
            // A private copy belongs to the library entry; the user's file stays.
            documentRepository.forget(uri)
            thumbnails.delete(uri)
            pendingRemoval.update { if (it == uri) null else it }
        }
    }

    override fun onCleared() = commitPendingRemoval()
}
