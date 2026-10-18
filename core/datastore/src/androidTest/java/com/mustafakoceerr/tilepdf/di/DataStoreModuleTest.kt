package com.mustafakoceerr.tilepdf.di

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Settings must survive an app update, so the file name can never change. */
@RunWith(AndroidJUnit4::class)
class DataStoreModuleTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = DataStoreModule.providesPreferencesDataStore(context)
    private val key = booleanPreferencesKey("smoke")

    @After
    fun tearDown() = runBlocking {
        store.edit { it.remove(key) }
        Unit
    }

    @Test
    fun storesPreferencesInTheExistingFile() = runBlocking {
        store.edit { it[key] = true }
        assertTrue(context.preferencesDataStoreFile("reader_preferences").exists())
    }
}
