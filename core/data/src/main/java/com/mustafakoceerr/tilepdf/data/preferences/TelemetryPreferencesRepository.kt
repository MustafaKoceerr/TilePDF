package com.mustafakoceerr.tilepdf.data.preferences

import com.mustafakoceerr.tilepdf.model.AnalyticsConsent
import com.mustafakoceerr.tilepdf.model.TelemetryPreferences
import kotlinx.coroutines.flow.Flow

/** The user's choices about crash reports and usage statistics. */
interface TelemetryPreferencesRepository {
    val preferences: Flow<TelemetryPreferences>
    suspend fun setCrashReportsEnabled(enabled: Boolean)
    suspend fun setAnalyticsConsent(consent: AnalyticsConsent)
}
