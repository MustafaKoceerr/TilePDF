package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PageSize

sealed interface ReaderUiState {
    data object Loading : ReaderUiState
    data class Ready(val pageSizes: List<PageSize>) : ReaderUiState
    data class Error(val reason: ReaderError) : ReaderUiState
}

enum class ReaderError { NotFound, PasswordProtected, Corrupt }
