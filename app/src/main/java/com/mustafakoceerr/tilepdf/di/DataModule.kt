package com.mustafakoceerr.tilepdf.di

import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.document.SafDocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.library.OfflineLibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.DataStoreReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.preferences.ReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.text.CachingTextSearchRepository
import com.mustafakoceerr.tilepdf.data.text.TextSearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    fun bindsDocumentRepository(impl: SafDocumentRepository): DocumentRepository

    @Binds
    fun bindsLibraryRepository(impl: OfflineLibraryRepository): LibraryRepository

    @Binds
    fun bindsReaderPreferencesRepository(
        impl: DataStoreReaderPreferencesRepository,
    ): ReaderPreferencesRepository

    @Binds
    fun bindsTextSearchRepository(impl: CachingTextSearchRepository): TextSearchRepository
}
