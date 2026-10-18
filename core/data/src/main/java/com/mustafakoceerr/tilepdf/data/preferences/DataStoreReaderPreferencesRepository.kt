package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
            volumeKeysTurnPages = prefs[VOLUME_KEYS_TURN_PAGES] ?: defaults.volumeKeysTurnPages,
            twoPageSpreads = prefs[TWO_PAGE_SPREADS] ?: defaults.twoPageSpreads,
            markupColor = prefs[MARKUP_COLOR] ?: defaults.markupColor,
            penColor = prefs[PEN_COLOR] ?: defaults.penColor,
            penWidthPt = prefs[PEN_WIDTH] ?: defaults.penWidthPt,
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

    override suspend fun setVolumeKeysTurnPages(enabled: Boolean) {
        dataStore.edit { it[VOLUME_KEYS_TURN_PAGES] = enabled }
    }

    override suspend fun setTwoPageSpreads(enabled: Boolean) {
        dataStore.edit { it[TWO_PAGE_SPREADS] = enabled }
    }

    override suspend fun setMarkupColor(color: Int) {
        dataStore.edit { it[MARKUP_COLOR] = color }
    }

    override suspend fun setPenColor(color: Int) {
        dataStore.edit { it[PEN_COLOR] = color }
    }

    override suspend fun setPenWidth(widthPt: Float) {
        dataStore.edit { it[PEN_WIDTH] = widthPt }
    }

    private companion object {
        val PAGE_THEME = stringPreferencesKey("page_theme")
        val READING_MODE = stringPreferencesKey("reading_mode")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val VOLUME_KEYS_TURN_PAGES = booleanPreferencesKey("volume_keys_turn_pages")
        val TWO_PAGE_SPREADS = booleanPreferencesKey("two_page_spreads")
        val LEGACY_NIGHT_MODE = booleanPreferencesKey("night_mode")
        val MARKUP_COLOR = intPreferencesKey("markup_color")
        val PEN_COLOR = intPreferencesKey("pen_color")
        val PEN_WIDTH = floatPreferencesKey("pen_width")

        /** A value written by a newer version reads as the default. */
        inline fun <reified T : Enum<T>> enumOrNull(name: String): T? =
            enumValues<T>().firstOrNull { it.name == name }
    }
}
