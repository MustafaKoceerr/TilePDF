package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.AppPalette
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreAppPaletteRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("app.preferences_pb") }

    @Test
    fun violetUntilAPaletteIsChosen() = runTest {
        assertEquals(AppPalette.Violet, DataStoreAppPaletteRepository(dataStore()).palette.first())
    }

    @Test
    fun theChosenPaletteIsRead() = runTest {
        val repository = DataStoreAppPaletteRepository(dataStore())
        repository.setPalette(AppPalette.Sand)
        assertEquals(AppPalette.Sand, repository.palette.first())
    }

    @Test
    fun aPaletteFromANewerVersionReadsAsViolet() = runTest {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("app_palette")] = "Aurora" }
        assertEquals(AppPalette.Violet, DataStoreAppPaletteRepository(store).palette.first())
    }

    @Test
    fun itSharesTheStoreWithTheReaderSettingsWithoutTouchingThem() = runTest {
        val store = dataStore()
        DataStoreReaderPreferencesRepository(store).setPenWidth(3f)
        DataStoreAppPaletteRepository(store).setPalette(AppPalette.Forest)
        assertEquals(3f, DataStoreReaderPreferencesRepository(store).preferences.first().penWidthPt)
    }
}
