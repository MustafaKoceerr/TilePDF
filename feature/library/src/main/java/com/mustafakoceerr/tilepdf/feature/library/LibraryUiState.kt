package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.model.LibraryDocument

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(val items: List<LibraryItem>) : LibraryUiState
}

data class LibraryItem(
    val document: LibraryDocument,
    /**
     * False when the app no longer holds a persisted read grant, e.g. the file
     * was moved or the provider revoked it; the user must pick it again.
     */
    val isAccessible: Boolean,
)
