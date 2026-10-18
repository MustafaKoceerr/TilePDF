package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.geometry.PdfRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.model.OutlineEntry
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
        val toc: TocUiState,
        val selection: SelectionUiState?,
    ) : ReaderUiState
}

enum class ReaderError { NotFound, PasswordProtected, Corrupt }

data class TocUiState(
    /** Table of contents; empty until loaded, or if the PDF has none. */
    val entries: List<OutlineEntry> = emptyList(),
    val isOpen: Boolean = false,
    /** Entry the reader was in when the sheet opened, or -1 before the first. */
    val currentIndex: Int = -1,
)

/** Selected text on one page; positions in top-left PDF points. */
data class SelectionUiState(
    val pageIndex: Int,
    val text: String,
    /** One per line, for drawing. */
    val rects: List<PdfRect>,
    /** Boxes of the first and last selected characters, where the handles sit. */
    val startBox: PdfRect,
    val endBox: PdfRect,
)

enum class SelectionHandle { Start, End }

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
