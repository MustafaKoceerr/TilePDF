package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.model.LibraryDocument

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(val documents: List<LibraryDocument>) : LibraryUiState
}
