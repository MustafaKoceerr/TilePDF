package com.mustafakoceerr.tilepdf.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.mustafakoceerr.tilepdf.data.local.DocumentDao
import com.mustafakoceerr.tilepdf.data.local.TilePdfDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): TilePdfDatabase =
        Room.databaseBuilder(context, TilePdfDatabase::class.java, "tilepdf.db").build()

    @Provides
    fun providesDocumentDao(database: TilePdfDatabase): DocumentDao = database.documentDao()

    @Provides
    @Singleton
    fun providesPreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("reader_preferences") }
}
