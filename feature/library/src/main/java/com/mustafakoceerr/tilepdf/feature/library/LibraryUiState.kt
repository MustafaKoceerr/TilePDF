package com.mustafakoceerr.tilepdf.feature.library

import com.mustafakoceerr.tilepdf.model.AppLanguage
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
        /** The language chosen in the app; [AppLanguage.System] follows the device. */
        val language: AppLanguage = AppLanguage.System,
    ) : LibraryUiState
}

data class LibraryItem(
    val document: LibraryDocument,
    /**
     * False when the app no longer holds a persisted read grant, e.g. the file
     * was moved or the provider revoked it; the user must pick it again.
     */
    val isAccessible: Boolean,
)
