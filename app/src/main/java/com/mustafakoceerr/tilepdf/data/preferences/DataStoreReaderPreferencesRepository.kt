package com.mustafakoceerr.tilepdf.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreReaderPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ReaderPreferencesRepository {

    override val nightMode: Flow<Boolean> = dataStore.data.map { it[NIGHT_MODE] ?: false }

    override suspend fun setNightMode(enabled: Boolean) {
        dataStore.edit { it[NIGHT_MODE] = enabled }
    }

    private companion object {
        val NIGHT_MODE = booleanPreferencesKey("night_mode")
    }
}
