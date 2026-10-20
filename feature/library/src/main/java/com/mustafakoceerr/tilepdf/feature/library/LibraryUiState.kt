package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.data.thumbnail.Thumbnail
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.LibrarySort

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    data class Success(
        val items: List<LibraryItem>,
        val query: String = "",
        /** The top bar shows the search field. */
        val searching: Boolean = false,
        val filter: LibraryFilter = LibraryFilter.All,
        val sort: LibrarySort = LibrarySort.Recent,
        /** Whether the library has any document at all, whatever the filter shows. */
        val hasDocuments: Boolean = items.isNotEmpty(),
        /** A removal waiting for its undo window to end; its row is hidden meanwhile. */
        val pendingRemoval: String? = null,
        /** Ask once whether usage statistics may be sent. */
        val askForStatistics: Boolean = false,
    ) : LibraryUiState
}

data class LibraryItem(
    val document: LibraryDocument,
    /**
     * False when the app no longer holds a persisted read grant, e.g. the file
     * was moved or the provider revoked it; the user must pick it again.
     */
    val isAccessible: Boolean,
    /** The first page, once the reader has drawn it. */
    val thumbnail: Thumbnail? = null,
)
