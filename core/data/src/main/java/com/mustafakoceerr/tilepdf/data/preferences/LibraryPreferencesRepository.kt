package com.mustafakoceerr.tilepdf.data.preferences

import com.mustafakoceerr.tilepdf.model.LibrarySort
import kotlinx.coroutines.flow.Flow

interface LibraryPreferencesRepository {
    val sort: Flow<LibrarySort>
    suspend fun setSort(sort: LibrarySort)
}
