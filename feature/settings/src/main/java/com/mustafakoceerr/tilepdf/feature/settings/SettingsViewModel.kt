package com.mustafakoceerr.tilepdf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustafakoceerr.languagemanager.LanguageManager
import com.mustafakoceerr.thememanager.ThemeManager
import com.mustafakoceerr.thememanager.ThemeMode
import com.mustafakoceerr.thememanager.ThemeState
import com.mustafakoceerr.thememanager.isDynamicColorAvailable
import com.mustafakoceerr.tilepdf.data.preferences.AppPaletteRepository
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
) : ViewModel() {

    /** The language follows the platform, so a change in system settings shows up too. */
    val uiState: StateFlow<SettingsUiState> = combine(
        themeManager.themeState,
        languageManager.languageMode,
        paletteRepository.palette,
    ) { theme, language, palette ->
        when (theme) {
            ThemeState.Loading -> SettingsUiState.Loading
            is ThemeState.Ready -> SettingsUiState.Success(
                theme = theme.settings,
                language = language.toAppLanguage(),
                palette = palette,
                dynamicColorAvailable = isDynamicColorAvailable,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState.Loading,
    )

    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { themeManager.setThemeMode(mode) }
    }

    fun onDynamicColorChanged(enabled: Boolean) {
        viewModelScope.launch { themeManager.setUseDynamicColor(enabled) }
    }

    /** Material You would cover the palette, so choosing one turns it off. */
    fun onPaletteSelected(palette: AppPalette) {
        viewModelScope.launch {
            paletteRepository.setPalette(palette)
            themeManager.setUseDynamicColor(false)
        }
    }

    /** The activity is recreated in the new language. Main thread only. */
    fun onLanguageSelected(language: AppLanguage) = languageManager.setLanguage(language.toLanguageMode())
}
