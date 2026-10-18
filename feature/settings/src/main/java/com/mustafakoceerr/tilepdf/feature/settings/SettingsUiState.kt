package com.mustafakoceerr.tilepdf.feature.settings

import com.mustafakoceerr.thememanager.ThemeSettings
import com.mustafakoceerr.tilepdf.model.AppLanguage

sealed interface SettingsUiState {
    /** The stored theme hasn't been read yet. */
    data object Loading : SettingsUiState

    data class Success(
        val theme: ThemeSettings,
        val language: AppLanguage,
        /** Material You needs Android 12; below it the switch is hidden. */
        val dynamicColorAvailable: Boolean,
    ) : SettingsUiState
}
