package com.mustafakoceerr.tilepdf.feature.settings

import android.os.Build
import androidx.activity.ComponentActivity
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

    private val state = SettingsUiState.Success(ThemeSettings(), AppLanguage.System, dynamicColorAvailable = true)

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

    /** Touch targets, labels and contrast, as the Accessibility Test Framework checks them. */
    @Test
    fun passesAccessibilityChecks() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        compose.enableAccessibilityChecks()
        compose.setContent { TilePDFTheme { SettingsScreen(state, onBack = {}) } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
