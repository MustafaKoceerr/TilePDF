package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mustafakoceerr.tilepdf.model.AppPalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreAppPaletteRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : AppPaletteRepository {

    // A name from a newer version reads as the default.
    override val palette: Flow<AppPalette> = dataStore.data.map { prefs ->
        AppPalette.entries.firstOrNull { it.name == prefs[PALETTE] } ?: AppPalette.Violet
    }

    override suspend fun setPalette(palette: AppPalette) {
        dataStore.edit { it[PALETTE] = palette.name }
    }

    private companion object {
        val PALETTE = stringPreferencesKey("app_palette")
    }
}
