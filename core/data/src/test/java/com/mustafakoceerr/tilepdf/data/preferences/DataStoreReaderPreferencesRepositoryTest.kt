package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReaderPreferences
import com.mustafakoceerr.tilepdf.model.ReadingMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreReaderPreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("reader.preferences_pb") }

    @Test
    fun defaultsWhenEmpty() = runTest {
        val repository = DataStoreReaderPreferencesRepository(dataStore())
        assertEquals(ReaderPreferences(), repository.preferences.first())
    }

    @Test
    fun setPageThemeIsRead() = runTest {
        val repository = DataStoreReaderPreferencesRepository(dataStore())
        repository.setPageTheme(PageTheme.Sepia)
        assertEquals(PageTheme.Sepia, repository.preferences.first().pageTheme)
    }

    @Test
    fun setReadingModeIsRead() = runTest {
        val repository = DataStoreReaderPreferencesRepository(dataStore())
        repository.setReadingMode(ReadingMode.Horizontal)
        assertEquals(ReadingMode.Horizontal, repository.preferences.first().readingMode)
    }

    @Test
    fun setKeepScreenOnIsRead() = runTest {
        val repository = DataStoreReaderPreferencesRepository(dataStore())
        repository.setKeepScreenOn(true)
        assertEquals(true, repository.preferences.first().keepScreenOn)
    }

    @Test
    fun legacyNightModeReadsAsNight() = runTest {
        // Written by versions that only had a night mode switch.
        val dataStore = dataStore()
        dataStore.edit { it[booleanPreferencesKey("night_mode")] = true }
        val repository = DataStoreReaderPreferencesRepository(dataStore)
        assertEquals(PageTheme.Night, repository.preferences.first().pageTheme)
    }

    @Test
    fun pageThemeWinsOverLegacy() = runTest {
        val dataStore = dataStore()
        dataStore.edit { it[booleanPreferencesKey("night_mode")] = true }
        val repository = DataStoreReaderPreferencesRepository(dataStore)
        repository.setPageTheme(PageTheme.Light)
        assertEquals(PageTheme.Light, repository.preferences.first().pageTheme)
    }

    @Test
    fun unknownThemeReadsAsLight() = runTest {
        val dataStore = dataStore()
        dataStore.edit { it[stringPreferencesKey("page_theme")] = "Purple" }
        val repository = DataStoreReaderPreferencesRepository(dataStore)
        assertEquals(PageTheme.Light, repository.preferences.first().pageTheme)
    }
}
