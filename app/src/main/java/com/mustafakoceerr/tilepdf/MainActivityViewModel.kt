package com.mustafakoceerr.tilepdf

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustafakoceerr.thememanager.ThemeManager
import com.mustafakoceerr.thememanager.ThemeState
import com.mustafakoceerr.tilepdf.data.preferences.AppPaletteRepository
import com.mustafakoceerr.tilepdf.model.AppPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    themeManager: ThemeManager,
    paletteRepository: AppPaletteRepository,
) : ViewModel() {
    /** [ThemeState.Loading] until the stored choice is read; the splash screen waits for it. */
    val themeState: StateFlow<ThemeState> = themeManager.themeState

    /** Null until the stored palette is read; the splash screen waits for it too. */
    val palette: StateFlow<AppPalette?> = paletteRepository.palette
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
}
