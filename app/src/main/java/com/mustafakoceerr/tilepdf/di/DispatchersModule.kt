package com.mustafakoceerr.tilepdf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/**
 * Single-threaded dispatcher for all [android.graphics.pdf.PdfRenderer] access.
 * PdfRenderer is not thread-safe and allows only one open page at a time.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PdfDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @PdfDispatcher
    fun providePdfDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
}
