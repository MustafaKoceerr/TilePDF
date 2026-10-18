package com.mustafakoceerr.tilepdf.di

import android.content.Context
import androidx.room.Room
import com.mustafakoceerr.tilepdf.data.local.AnnotationDraftDao
import com.mustafakoceerr.tilepdf.data.local.BookmarkDao
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
    fun providesBookmarkDao(database: TilePdfDatabase): BookmarkDao = database.bookmarkDao()

    @Provides
    fun providesAnnotationDraftDao(database: TilePdfDatabase): AnnotationDraftDao = database.annotationDraftDao()
}
