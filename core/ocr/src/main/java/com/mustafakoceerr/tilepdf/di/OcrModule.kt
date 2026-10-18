package com.mustafakoceerr.tilepdf.di

import com.mustafakoceerr.tilepdf.core.ocr.MlKitTextRecognizer
import com.mustafakoceerr.tilepdf.core.ocr.TextRecognizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface OcrModule {
    @Binds
    fun bindsTextRecognizer(impl: MlKitTextRecognizer): TextRecognizer
}
