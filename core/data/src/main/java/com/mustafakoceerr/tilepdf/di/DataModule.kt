package com.mustafakoceerr.tilepdf.di

import com.mustafakoceerr.tilepdf.data.annotation.AnnotationRepository
import com.mustafakoceerr.tilepdf.data.annotation.OfflineAnnotationRepository
import com.mustafakoceerr.tilepdf.data.document.DocumentRepository
import com.mustafakoceerr.tilepdf.data.document.SafDocumentRepository
import com.mustafakoceerr.tilepdf.data.library.LibraryRepository
import com.mustafakoceerr.tilepdf.data.library.OfflineLibraryRepository
import com.mustafakoceerr.tilepdf.data.preferences.DataStoreReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.preferences.ReaderPreferencesRepository
import com.mustafakoceerr.tilepdf.data.text.CachingDocumentTextRepository
import com.mustafakoceerr.tilepdf.data.text.DocumentTextRepository
import com.mustafakoceerr.tilepdf.data.bookmark.BookmarkRepository
import com.mustafakoceerr.tilepdf.data.bookmark.OfflineBookmarkRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindsAnnotationRepository(impl: OfflineAnnotationRepository): AnnotationRepository

    @Binds
    fun bindsBookmarkRepository(impl: OfflineBookmarkRepository): BookmarkRepository

    @Binds
    fun bindsDocumentRepository(impl: SafDocumentRepository): DocumentRepository

    @Binds
    fun bindsLibraryRepository(impl: OfflineLibraryRepository): LibraryRepository

    @Binds
    fun bindsReaderPreferencesRepository(
        impl: DataStoreReaderPreferencesRepository,
    ): ReaderPreferencesRepository

    @Binds
    fun bindsDocumentTextRepository(impl: CachingDocumentTextRepository): DocumentTextRepository
}
