package com.mustafakoceerr.tilepdf

import android.app.Application
import com.mustafakoceerr.tilepdf.di.ApplicationScope
import com.mustafakoceerr.tilepdf.domain.CleanUpCopiesUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TilePdfApp : Application() {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var cleanUpCopies: CleanUpCopiesUseCase

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch { runCatching { cleanUpCopies() } }
    }
}
