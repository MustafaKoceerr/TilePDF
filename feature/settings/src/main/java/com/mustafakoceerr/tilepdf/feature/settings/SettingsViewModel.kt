package com.mustafakoceerr.tilepdf.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustafakoceerr.languagemanager.LanguageManager
import com.mustafakoceerr.thememanager.ThemeManager
import com.mustafakoceerr.thememanager.ThemeMode
import com.mustafakoceerr.thememanager.ThemeState
import com.mustafakoceerr.thememanager.isDynamicColorAvailable
import com.mustafakoceerr.tilepdf.model.AppLanguage
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
) : ViewModel() {

    /** The language follows the platform, so a change in system settings shows up too. */
    val uiState: StateFlow<SettingsUiState> = combine(
        themeManager.themeState,
        languageManager.languageMode,
    ) { theme, language ->
        when (theme) {
            ThemeState.Loading -> SettingsUiState.Loading
            is ThemeState.Ready -> SettingsUiState.Success(
                theme = theme.settings,
                language = language.toAppLanguage(),
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

    /** The activity is recreated in the new language. Main thread only. */
    fun onLanguageSelected(language: AppLanguage) = languageManager.setLanguage(language.toLanguageMode())
}
