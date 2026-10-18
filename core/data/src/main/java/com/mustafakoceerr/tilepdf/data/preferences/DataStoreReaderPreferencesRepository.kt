package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReaderPreferences
import com.mustafakoceerr.tilepdf.model.ReadingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreReaderPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesRepository {

    override val preferences: Flow<ReaderPreferences> = dataStore.data.map { prefs ->
        val defaults = ReaderPreferences()
        ReaderPreferences(
            // Older versions only had a night mode switch.
            pageTheme = prefs[PAGE_THEME]?.let { enumOrNull<PageTheme>(it) ?: defaults.pageTheme }
                ?: if (prefs[LEGACY_NIGHT_MODE] == true) PageTheme.Night else defaults.pageTheme,
            readingMode = prefs[READING_MODE]?.let { enumOrNull<ReadingMode>(it) } ?: defaults.readingMode,
            keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
        )
    }

    override suspend fun setPageTheme(theme: PageTheme) {
        dataStore.edit {
            it[PAGE_THEME] = theme.name
            it.remove(LEGACY_NIGHT_MODE)
        }
    }

    override suspend fun setReadingMode(mode: ReadingMode) {
        dataStore.edit { it[READING_MODE] = mode.name }
    }

    override suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    private companion object {
        val PAGE_THEME = stringPreferencesKey("page_theme")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val LEGACY_NIGHT_MODE = booleanPreferencesKey("night_mode")

        /** A value written by a newer version reads as the default. */
        inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
            enumValues<T>().firstOrNull { it.name == name }
    }
}
