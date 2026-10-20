package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.core.analytics.TelemetrySwitches
import com.mustafakoceerr.tilepdf.data.preferences.TelemetryPreferencesRepository
import com.mustafakoceerr.tilepdf.model.AnalyticsConsent
import com.mustafakoceerr.tilepdf.model.TelemetryPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyTelemetryPreferencesUseCaseTest {

    private class FakeTelemetryPreferences : TelemetryPreferencesRepository {
        override val preferences = MutableStateFlow(TelemetryPreferences())
        override suspend fun setCrashReportsEnabled(enabled: Boolean) =
            preferences.update { it.copy(crashReportsEnabled = enabled) }
        override suspend fun setAnalyticsConsent(consent: AnalyticsConsent) =
            preferences.update { it.copy(analyticsConsent = consent) }
    }

    private class RecordingSwitches : TelemetrySwitches {
        val crashReports = mutableListOf<Boolean>()
        val analytics = mutableListOf<Boolean>()
        override fun setCrashReportsEnabled(enabled: Boolean) {
            crashReports += enabled
        }
        override fun setAnalyticsEnabled(enabled: Boolean) {
            analytics += enabled
        }
    }

    private val preferences = FakeTelemetryPreferences()
    private val switches = RecordingSwitches()

    private fun TestScope.applying() =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { ApplyTelemetryPreferencesUseCase(preferences, switches)() }

    @Test
    fun crashReportsOnAndStatisticsOffUntilAllowed() = runTest {
        applying()
        assertEquals(listOf(true), switches.crashReports)
        assertEquals(listOf(false), switches.analytics)
    }

    @Test
    fun statisticsFollowTheConsent() = runTest {
        applying()
        preferences.setAnalyticsConsent(AnalyticsConsent.Granted)
        preferences.setAnalyticsConsent(AnalyticsConsent.Denied)
        assertEquals(listOf(false, true, false), switches.analytics)
    }

    @Test
    fun crashReportsFollowTheSwitch() = runTest {
        applying()
        preferences.setCrashReportsEnabled(false)
        assertEquals(listOf(true, false), switches.crashReports)
    }

    @Test
    fun oneChoiceDoesNotReapplyTheOther() = runTest {
        applying()
        preferences.setAnalyticsConsent(AnalyticsConsent.Granted)
        assertEquals(listOf(true), switches.crashReports)
    }
}
