package com.mustafakoceerr.tilepdf.di

import com.mustafakoceerr.tilepdf.core.pdf.PdfEngine
import com.mustafakoceerr.tilepdf.core.pdf.Pdfium
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface PdfModule {

    @Binds
    fun bindsPdfEngine(impl: Pdfium): PdfEngine
}
