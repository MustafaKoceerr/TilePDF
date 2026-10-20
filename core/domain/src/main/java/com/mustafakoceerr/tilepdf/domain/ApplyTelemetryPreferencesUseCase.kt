package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.core.analytics.TelemetrySwitches
import com.mustafakoceerr.tilepdf.data.preferences.TelemetryPreferencesRepository
import com.mustafakoceerr.tilepdf.model.AnalyticsConsent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps crash reports and statistics as the user chose; runs for the app's lifetime. */
class ApplyTelemetryPreferencesUseCase @Inject constructor(
    private val preferences: TelemetryPreferencesRepository,
    private val switches: TelemetrySwitches,
) {
    suspend operator fun invoke() = coroutineScope {
        launch {
            preferences.preferences.map { it.crashReportsEnabled }.distinctUntilChanged()
                .collect(switches::setCrashReportsEnabled)
        }
        launch {
            // Statistics only with a yes; not asked yet counts as no.
            preferences.preferences.map { it.analyticsConsent == AnalyticsConsent.Granted }.distinctUntilChanged()
                .collect(switches::setAnalyticsEnabled)
        }
    }
}
