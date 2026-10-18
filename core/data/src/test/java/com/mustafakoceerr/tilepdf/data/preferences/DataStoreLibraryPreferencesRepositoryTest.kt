package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.LibrarySort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreLibraryPreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("library.preferences_pb") }

    @Test
    fun defaultsToRecent() = runTest {
        assertEquals(LibrarySort.Recent, DataStoreLibraryPreferencesRepository(dataStore()).sort.first())
    }

    @Test
    fun setSortIsRead() = runTest {
        val repository = DataStoreLibraryPreferencesRepository(dataStore())
        repository.setSort(LibrarySort.Name)
        assertEquals(LibrarySort.Name, repository.sort.first())
    }

    @Test
    fun unknownReadsAsRecent() = runTest {
        val dataStore = dataStore()
        dataStore.edit { it[stringPreferencesKey("library_sort")] = "Size" }
        assertEquals(LibrarySort.Recent, DataStoreLibraryPreferencesRepository(dataStore).sort.first())
    }
}
