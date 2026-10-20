package com.mustafakoceerr.tilepdf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustafakoceerr.languagemanager.LanguageManager
import com.mustafakoceerr.thememanager.ThemeManager
import com.mustafakoceerr.thememanager.ThemeMode
import com.mustafakoceerr.thememanager.ThemeState
import com.mustafakoceerr.thememanager.isDynamicColorAvailable
import com.mustafakoceerr.tilepdf.core.analytics.AnalyticsEvent
import com.mustafakoceerr.tilepdf.core.analytics.AnalyticsHelper
import com.mustafakoceerr.tilepdf.data.preferences.AppPaletteRepository
import com.mustafakoceerr.tilepdf.data.preferences.TelemetryPreferencesRepository
import com.mustafakoceerr.tilepdf.model.AnalyticsConsent
import com.mustafakoceerr.tilepdf.model.AppLanguage
import com.mustafakoceerr.tilepdf.model.AppPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themeManager: ThemeManager,
    private val languageManager: LanguageManager,
    private val paletteRepository: AppPaletteRepository,
    private val telemetryPreferences: TelemetryPreferencesRepository,
    private val analytics: AnalyticsHelper,
) : ViewModel() {

    /** The language follows the platform, so a change in system settings shows up too. */
    val uiState: StateFlow<SettingsUiState> = combine(
        themeManager.themeState,
        languageManager.languageMode,
        paletteRepository.palette,
        telemetryPreferences.preferences,
    ) { theme, language, palette, telemetry ->
        when (theme) {
            ThemeState.Loading -> SettingsUiState.Loading
            is ThemeState.Ready -> SettingsUiState.Success(
                theme = theme.settings,
                language = language.toAppLanguage(),
                palette = palette,
                dynamicColorAvailable = isDynamicColorAvailable,
                crashReportsEnabled = telemetry.crashReportsEnabled,
                analyticsEnabled = telemetry.analyticsConsent == AnalyticsConsent.Granted,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState.Loading,
    )

    fun onThemeModeSelected(mode: ThemeMode) {
        analytics.logEvent(AnalyticsEvent("theme_selected", mapOf("mode" to mode.name)))
        viewModelScope.launch { themeManager.setThemeMode(mode) }
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch { themeManager.setUseDynamicColor(enabled) }
    }

    /** Material You would cover the palette, so choosing one turns it off. */
    fun onPaletteSelected(palette: AppPalette) {
        analytics.logEvent(AnalyticsEvent("palette_selected", mapOf("palette" to palette.name)))
        viewModelScope.launch {
            paletteRepository.setPalette(palette)
            themeManager.setUseDynamicColor(false)
        }
    }

    fun onCrashReportsChanged(enabled: Boolean) {
        viewModelScope.launch { telemetryPreferences.setCrashReportsEnabled(enabled) }
    }

    /** Turning statistics off is a no: the library won't ask again. */
    fun onAnalyticsChanged(enabled: Boolean) {
        viewModelScope.launch {
            telemetryPreferences.setAnalyticsConsent(if (enabled) AnalyticsConsent.Granted else AnalyticsConsent.Denied)
        }
    }

    /** The activity is recreated in the new language. Main thread only. */
    fun onLanguageSelected(language: AppLanguage) = languageManager.setLanguage(language.toLanguageMode())
}
