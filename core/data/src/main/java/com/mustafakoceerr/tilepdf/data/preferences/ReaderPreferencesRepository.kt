package com.mustafakoceerr.tilepdf.data.preferences

import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReaderPreferences
import com.mustafakoceerr.tilepdf.model.ReadingMode
import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
    val preferences: Flow<ReaderPreferences>
    suspend fun setPageTheme(theme: PageTheme)
    suspend fun setReadingMode(mode: ReadingMode)
    suspend fun setKeepScreenOn(enabled: Boolean)
    suspend fun setMarkupColor(color: Int)
    suspend fun setPenColor(color: Int)
    suspend fun setPenWidth(widthPt: Float)
}
