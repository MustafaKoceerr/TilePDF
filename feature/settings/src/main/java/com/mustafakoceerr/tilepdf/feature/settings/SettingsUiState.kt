package com.mustafakoceerr.tilepdf.feature.settings

import com.mustafakoceerr.thememanager.ThemeSettings
import com.mustafakoceerr.tilepdf.model.AppLanguage
import com.mustafakoceerr.tilepdf.model.AppPalette

sealed interface SettingsUiState {
    /** The stored theme hasn't been read yet. */
    data object Loading : SettingsUiState

    data class Success(
        val theme: ThemeSettings,
        val language: AppLanguage,
        val palette: AppPalette,
        /** Material You needs Android 12; below it the switch is hidden. */
        val dynamicColorAvailable: Boolean,
        val crashReportsEnabled: Boolean = true,
        /** On only once the user agreed to statistics. */
        val analyticsEnabled: Boolean = false,
    ) : SettingsUiState
}
