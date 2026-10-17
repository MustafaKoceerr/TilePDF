package com.mustafakoceerr.tilepdf.di

import com.mustafakoceerr.tilepdf.data.preferences.AppLanguageRepository
import com.mustafakoceerr.tilepdf.language.AppCompatLanguageRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal interface LanguageModule {
    @Binds
    fun bindsAppLanguageRepository(repository: AppCompatLanguageRepository): AppLanguageRepository
}
