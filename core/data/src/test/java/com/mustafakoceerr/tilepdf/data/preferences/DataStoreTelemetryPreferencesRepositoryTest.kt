package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.AnalyticsConsent
import com.mustafakoceerr.tilepdf.model.AppPalette
import com.mustafakoceerr.tilepdf.model.TelemetryPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreTelemetryPreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.dataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.newFile("app.preferences_pb") }

    @Test
    fun crashReportsOnAndStatisticsUnaskedAtFirst() = runTest {
        assertEquals(TelemetryPreferences(), DataStoreTelemetryPreferencesRepository(dataStore()).preferences.first())
    }

    @Test
    fun crashReportsCanBeTurnedOff() = runTest {
        val repository = DataStoreTelemetryPreferencesRepository(dataStore())
        repository.setCrashReportsEnabled(false)
        assertEquals(false, repository.preferences.first().crashReportsEnabled)
    }

    @Test
    fun theConsentIsRead() = runTest {
        val repository = DataStoreTelemetryPreferencesRepository(dataStore())
        repository.setAnalyticsConsent(AnalyticsConsent.Granted)
        assertEquals(AnalyticsConsent.Granted, repository.preferences.first().analyticsConsent)
        repository.setAnalyticsConsent(AnalyticsConsent.Denied)
        assertEquals(AnalyticsConsent.Denied, repository.preferences.first().analyticsConsent)
    }

    @Test
    fun anUnknownConsentReadsAsUnasked() = runTest {
        // Never as granted: statistics need a yes the user actually gave.
        val store = dataStore()
        store.edit { it[stringPreferencesKey("analytics_consent")] = "Partial" }
        assertEquals(AnalyticsConsent.Unasked, DataStoreTelemetryPreferencesRepository(store).preferences.first().analyticsConsent)
    }

    @Test
    fun itSharesTheStoreWithThePaletteWithoutTouchingIt() = runTest {
        val store = dataStore()
        DataStoreAppPaletteRepository(store).setPalette(AppPalette.Ocean)
        DataStoreTelemetryPreferencesRepository(store).setCrashReportsEnabled(false)
        assertEquals(AppPalette.Ocean, DataStoreAppPaletteRepository(store).palette.first())
    }
}
