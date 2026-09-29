package com.mustafakoceerr.tilepdf.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = libraryRepository.observeDocuments()
        .map { documents ->
            val accessible = documentRepository.accessibleUris()
            LibraryUiState.Success(documents.map { LibraryItem(it, it.uri in accessible) })
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading,
        )

    /** A file came back from the system picker. */
    fun onDocumentPicked(uri: String) {
        documentRepository.persistAccess(uri)
    }

    fun onRemove(uri: String) {
        viewModelScope.launch { libraryRepository.remove(uri) }
    }
}
