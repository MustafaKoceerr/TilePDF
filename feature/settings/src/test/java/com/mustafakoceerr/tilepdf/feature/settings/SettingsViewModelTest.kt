package com.mustafakoceerr.tilepdf.feature.settings

import com.mustafakoceerr.languagemanager.LanguageMode
import com.mustafakoceerr.languagemanager.testing.FakeLanguageManager
import com.mustafakoceerr.thememanager.ThemeMode
import com.mustafakoceerr.thememanager.ThemeSettings
import com.mustafakoceerr.thememanager.ThemeState
import com.mustafakoceerr.thememanager.testing.FakeThemeManager
import com.mustafakoceerr.tilepdf.data.preferences.AppPaletteRepository
import com.mustafakoceerr.tilepdf.model.AppLanguage
import com.mustafakoceerr.tilepdf.model.AppPalette
import kotlinx.coroutines.flow.MutableStateFlow
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

    private class FakeAppPaletteRepository : AppPaletteRepository {
        override val palette = MutableStateFlow(AppPalette.Violet)
        override suspend fun setPalette(palette: AppPalette) {
            this.palette.value = palette
        }
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Keeps the state flow collected, as the screen does. */
    private fun TestScope.collected(viewModel: SettingsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }

    private fun TestScope.viewModel() = SettingsViewModel(theme, language, palettes).also { collected(it) }

    private suspend fun SettingsViewModel.success() =
        uiState.first { it is SettingsUiState.Success } as SettingsUiState.Success

    @Test
    fun loadingUntilThemeIsRead() = runTest {
        val loading = FakeThemeManager(ThemeState.Loading)
        val viewModel = SettingsViewModel(loading, language, palettes).also { collected(it) }
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
}
