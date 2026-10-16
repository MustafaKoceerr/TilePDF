package com.mustafakoceerr.tilepdf.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

/** Scope for work that must outlive any screen, e.g. releasing resources. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

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

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
