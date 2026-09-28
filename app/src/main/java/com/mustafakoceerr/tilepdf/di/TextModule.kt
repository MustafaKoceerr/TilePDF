package com.mustafakoceerr.tilepdf.di

import android.os.Build
import com.mustafakoceerr.tilepdf.data.text.NativePdfTextProvider
import com.mustafakoceerr.tilepdf.data.text.PdfBoxTextProvider
import com.mustafakoceerr.tilepdf.data.text.PdfTextProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider

/** Picks the text extraction strategy for the running API level. */
@Module
@InstallIn(SingletonComponent::class)
object TextModule {

    @Provides
    fun providesTextProviderFactory(
        pdfBox: Provider<PdfBoxTextProvider.Factory>,
        native: Provider<NativePdfTextProvider.Factory>,
    ): PdfTextProvider.Factory =
        if (Build.VERSION.SDK_INT >= 35) native.get() else pdfBox.get()
}
