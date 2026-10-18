package com.mustafakoceerr.tilepdf.data.preferences

import kotlinx.coroutines.flow.Flow

interface ReaderPreferencesRepository {
    val nightMode: Flow<Boolean>
    suspend fun toggleNightMode()
}
