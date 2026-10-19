package com.mustafakoceerr.tilepdf.di

import com.mustafakoceerr.tilepdf.core.pdf.PdfEngine
import com.mustafakoceerr.tilepdf.core.pdf.PdfThread
import com.mustafakoceerr.tilepdf.core.pdf.Pdfium
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
interface PdfModule {

    @Binds
    fun bindsPdfEngine(impl: Pdfium): PdfEngine

    companion object {
        @Provides
        @PdfDispatcher
        fun providesPdfDispatcher(): CoroutineDispatcher = PdfThread.dispatcher
    }
}
