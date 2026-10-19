package com.mustafakoceerr.tilepdf.feature.settings

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.thememanager.ThemeMode
import com.mustafakoceerr.thememanager.ThemeSettings
import com.mustafakoceerr.tilepdf.model.AppLanguage
import com.mustafakoceerr.tilepdf.model.AppPalette
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private val state = SettingsUiState.Success(ThemeSettings(), AppLanguage.System, AppPalette.Violet, dynamicColorAvailable = true)

    @Test
    fun selectingDarkReports() {
        var mode: ThemeMode? = null
        compose.setContent { TilePDFTheme { SettingsScreen(state, onBack = {}, onThemeModeSelected = { mode = it }) } }
        compose.onNodeWithText(text(R.string.settings_theme_dark)).performClick()
        assertEquals(ThemeMode.DARK, mode)
    }

    @Test
    fun selectingTurkishReports() {
        var language: AppLanguage? = null
        compose.setContent { TilePDFTheme { SettingsScreen(state, onBack = {}, onLanguageSelected = { language = it }) } }
        compose.onNodeWithText("Türkçe").performClick()
        assertEquals(AppLanguage.Turkish, language)
    }

    @Test
    fun dynamicColorToggles() {
        var enabled: Boolean? = null
        compose.setContent { TilePDFTheme { SettingsScreen(state, onBack = {}, onDynamicColorChanged = { enabled = it }) } }
        compose.onNodeWithText(text(R.string.settings_dynamic_color)).performClick()
        assertEquals(true, enabled)
    }

    @Test
    fun choosingAPaletteReportsIt() {
        var palette: AppPalette? = null
        compose.setContent { TilePDFTheme { SettingsScreen(state, onBack = {}, onPaletteSelected = { palette = it }) } }
        compose.onNodeWithText(text(R.string.palette_ocean)).performClick()
        assertEquals(AppPalette.Ocean, palette)
    }

    @Test
    fun theChosenPaletteIsSelected() {
        compose.setContent { TilePDFTheme { SettingsScreen(state.copy(palette = AppPalette.Forest), onBack = {}) } }
        compose.onNodeWithText(text(R.string.palette_forest)).assertIsSelected()
        compose.onNodeWithText(text(R.string.palette_violet)).assertIsNotSelected()
    }

    @Test
    fun noPaletteIsSelectedWhileMaterialYouCoversThem() {
        val dynamic = state.copy(theme = ThemeSettings(useDynamicColor = true), palette = AppPalette.Forest)
        compose.setContent { TilePDFTheme { SettingsScreen(dynamic, onBack = {}) } }
        compose.onNodeWithText(text(R.string.palette_forest)).assertIsNotSelected()
    }

    /** Touch targets, labels and contrast, as the Accessibility Test Framework checks them. */
    @Test
    fun passesAccessibilityChecks() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        compose.enableAccessibilityChecks()
        compose.setContent { TilePDFTheme { SettingsScreen(state, onBack = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
