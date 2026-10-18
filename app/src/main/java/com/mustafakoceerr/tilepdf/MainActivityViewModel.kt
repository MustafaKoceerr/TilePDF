package com.mustafakoceerr.tilepdf

import androidx.lifecycle.ViewModel
import com.mustafakoceerr.thememanager.ThemeManager
import com.mustafakoceerr.thememanager.ThemeState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class MainActivityViewModel @Inject constructor(themeManager: ThemeManager) : ViewModel() {
    /** [ThemeState.Loading] until the stored choice is read; the splash screen waits for it. */
    val themeState: StateFlow<ThemeState> = themeManager.themeState
}
