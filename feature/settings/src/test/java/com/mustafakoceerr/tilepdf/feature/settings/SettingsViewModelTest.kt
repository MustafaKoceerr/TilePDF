package com.mustafakoceerr.tilepdf.feature.settings

import com.mustafakoceerr.languagemanager.LanguageMode
import com.mustafakoceerr.languagemanager.testing.FakeLanguageManager
import com.mustafakoceerr.thememanager.ThemeMode
import com.mustafakoceerr.thememanager.ThemeSettings
import com.mustafakoceerr.thememanager.ThemeState
import com.mustafakoceerr.thememanager.testing.FakeThemeManager
import com.mustafakoceerr.tilepdf.core.analytics.AnalyticsEvent
import com.mustafakoceerr.tilepdf.core.analytics.AnalyticsHelper
import com.mustafakoceerr.tilepdf.data.preferences.AppPaletteRepository
import com.mustafakoceerr.tilepdf.data.preferences.TelemetryPreferencesRepository
import com.mustafakoceerr.tilepdf.model.AnalyticsConsent
import com.mustafakoceerr.tilepdf.model.AppLanguage
import com.mustafakoceerr.tilepdf.model.AppPalette
import com.mustafakoceerr.tilepdf.model.TelemetryPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val theme = FakeThemeManager()
    private val language = FakeLanguageManager()
    private val palettes = FakeAppPaletteRepository()
    private val telemetry = FakeTelemetryPreferences()
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = AnalyticsHelper { events += it }

    private class FakeAppPaletteRepository : AppPaletteRepository {
        override val palette = MutableStateFlow(AppPalette.Violet)
        override suspend fun setPalette(palette: AppPalette) {
            this.palette.value = palette
        }
    }

    private class FakeTelemetryPreferences : TelemetryPreferencesRepository {
        override val preferences = MutableStateFlow(TelemetryPreferences())
        override suspend fun setCrashReportsEnabled(enabled: Boolean) =
            preferences.update { it.copy(crashReportsEnabled = enabled) }
        override suspend fun setAnalyticsConsent(consent: AnalyticsConsent) =
            preferences.update { it.copy(analyticsConsent = consent) }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Keeps the state flow collected, as the screen does. */
    private fun TestScope.collected(viewModel: SettingsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    private fun TestScope.viewModel() = SettingsViewModel(theme, language, palettes, telemetry, analytics).also { collected(it) }

    private suspend fun SettingsViewModel.success() =
        uiState.first { it is SettingsUiState.Success } as SettingsUiState.Success

    @Test
    fun loadingUntilThemeIsRead() = runTest {
        val loading = FakeThemeManager(ThemeState.Loading)
        val viewModel = SettingsViewModel(loading, language, palettes, telemetry, analytics).also { collected(it) }
        assertEquals(SettingsUiState.Loading, viewModel.uiState.value)
        loading.emit(ThemeState.Ready(ThemeSettings(mode = ThemeMode.DARK)))
        assertEquals(ThemeMode.DARK, viewModel.success().theme.mode)
    }

    @Test
    fun themeModeIsSaved() = runTest {
        val viewModel = viewModel()
        viewModel.onThemeModeSelected(ThemeMode.DARK)
        assertEquals(listOf(ThemeMode.DARK), theme.setThemeModeCalls)
        assertEquals(ThemeMode.DARK, viewModel.success().theme.mode)
    }

    @Test
    fun dynamicColorIsSaved() = runTest {
        val viewModel = viewModel()
        viewModel.onDynamicColorChanged(true)
        assertEquals(listOf(true), theme.setUseDynamicColorCalls)
        assertEquals(true, viewModel.success().theme.useDynamicColor)
    }

    @Test
    fun paletteIsSavedAndShown() = runTest {
        val viewModel = viewModel()
        viewModel.onPaletteSelected(AppPalette.Sand)
        assertEquals(AppPalette.Sand, palettes.palette.value)
        assertEquals(AppPalette.Sand, viewModel.success().palette)
    }

    @Test
    fun choosingAPaletteTurnsMaterialYouOff() = runTest {
        // Material You colours would hide the palette.
        val viewModel = viewModel()
        viewModel.onDynamicColorChanged(true)
        viewModel.onPaletteSelected(AppPalette.Forest)
        assertEquals(listOf(true, false), theme.setUseDynamicColorCalls)
        assertEquals(false, viewModel.success().theme.useDynamicColor)
    }

    @Test
    fun languageIsSet() = runTest {
        val viewModel = viewModel()
        viewModel.onLanguageSelected(AppLanguage.Turkish)
        assertEquals(listOf(LanguageMode.Custom("tr")), language.setLanguageCalls)
        assertEquals(AppLanguage.Turkish, viewModel.success().language)
    }

    @Test
    fun externalLanguageChangeIsShown() = runTest {
        val viewModel = viewModel()
        language.simulateExternalChange(LanguageMode.Custom("en-US"))
        assertEquals(AppLanguage.English, viewModel.success().language)
    }

    @Test
    fun crashReportsAreOnAndStatisticsOffAtFirst() = runTest {
        val state = viewModel().success()
        assertEquals(true, state.crashReportsEnabled)
        assertEquals(false, state.analyticsEnabled)
    }

    @Test
    fun crashReportsCanBeTurnedOff() = runTest {
        val viewModel = viewModel()
        viewModel.onCrashReportsChanged(false)
        assertEquals(false, telemetry.preferences.value.crashReportsEnabled)
        assertEquals(false, viewModel.success().crashReportsEnabled)
    }

    @Test
    fun theStatisticsSwitchGivesOrWithdrawsConsent() = runTest {
        val viewModel = viewModel()
        viewModel.onAnalyticsChanged(true)
        assertEquals(AnalyticsConsent.Granted, telemetry.preferences.value.analyticsConsent)
        assertEquals(true, viewModel.success().analyticsEnabled)
        viewModel.onAnalyticsChanged(false)
        assertEquals(AnalyticsConsent.Denied, telemetry.preferences.value.analyticsConsent)
    }

    @Test
    fun appearanceChoicesAreCountedByName() = runTest {
        val viewModel = viewModel()
        viewModel.onThemeModeSelected(ThemeMode.DARK)
        viewModel.onPaletteSelected(AppPalette.Sand)
        assertEquals(
            listOf(
                AnalyticsEvent("theme_selected", mapOf("mode" to "DARK")),
                AnalyticsEvent("palette_selected", mapOf("palette" to "Sand")),
            ),
            events,
        )
    }
}
