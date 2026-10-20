package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.AnalyticsConsent
import com.mustafakoceerr.tilepdf.model.TelemetryPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreTelemetryPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : TelemetryPreferencesRepository {

    // An unknown consent reads as not asked yet, never as granted.
    override val preferences: Flow<TelemetryPreferences> = dataStore.data.map { prefs ->
        TelemetryPreferences(
            crashReportsEnabled = prefs[CRASH_REPORTS] ?: true,
            analyticsConsent = AnalyticsConsent.entries.firstOrNull { it.name == prefs[ANALYTICS_CONSENT] }
                ?: AnalyticsConsent.Unasked,
        )
    }

    override suspend fun setCrashReportsEnabled(enabled: Boolean) {
        dataStore.edit { it[CRASH_REPORTS] = enabled }
    }

    override suspend fun setAnalyticsConsent(consent: AnalyticsConsent) {
        dataStore.edit { it[ANALYTICS_CONSENT] = consent.name }
    }

    private companion object {
        val CRASH_REPORTS = booleanPreferencesKey("crash_reports_enabled")
        val ANALYTICS_CONSENT = stringPreferencesKey("analytics_consent")
    }
}
