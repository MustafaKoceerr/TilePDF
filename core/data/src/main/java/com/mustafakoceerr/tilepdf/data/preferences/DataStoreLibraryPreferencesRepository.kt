package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.LibrarySort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreLibraryPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : LibraryPreferencesRepository {

    // A value written by a newer version reads as the default.
    override val sort: Flow<LibrarySort> = dataStore.data.map { prefs ->
        LibrarySort.entries.firstOrNull { it.name == prefs[SORT] } ?: LibrarySort.Recent
    }

    override suspend fun setSort(sort: LibrarySort) {
        dataStore.edit { it[SORT] = sort.name }
    }

    private companion object {
        val SORT = stringPreferencesKey("library_sort")
    }
}
