package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.model.SearchMatch

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Error(val reason: ReaderError) : ReaderUiState

    data class Ready(
        val title: String,
        val pageSizes: List<PageSize>,
        val pageImages: PageImageSource,
        /** Where to start; read once when the page list is first shown. */
        val initialPosition: ReadingPosition,
        val nightMode: Boolean,
        val search: SearchUiState,
    ) : ReaderUiState
}

enum class ReaderError { NotFound, PasswordProtected, Corrupt }

data class SearchUiState(
    /** Whether the search bar is shown. */
    val isActive: Boolean = false,
    val query: String = "",
    /** In document order; grows while [isSearching]. */
    val matches: List<SearchMatch> = emptyList(),
    /** Index into [matches] of the match being shown, or -1. */
    val currentIndex: Int = -1,
    val isSearching: Boolean = false,
) {
    val currentMatch: SearchMatch? get() = matches.getOrNull(currentIndex)
}
