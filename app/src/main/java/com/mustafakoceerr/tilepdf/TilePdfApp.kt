package com.mustafakoceerr.tilepdf

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.mustafakoceerr.tilepdf.di.ApplicationScope
import com.mustafakoceerr.tilepdf.domain.ApplyTelemetryPreferencesUseCase
import com.mustafakoceerr.tilepdf.domain.CleanUpCopiesUseCase
import com.mustafakoceerr.tilepdf.domain.ResumePendingSavesUseCase
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TilePdfApp : Application(), Configuration.Provider {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var applyTelemetryPreferences: ApplyTelemetryPreferencesUseCase

    @Inject
    lateinit var cleanUpCopies: CleanUpCopiesUseCase

    @Inject
    lateinit var resumePendingSaves: ResumePendingSavesUseCase

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Crash reports and statistics follow the user's choices for as long as the app runs.
        applicationScope.launch { applyTelemetryPreferences() }
        applicationScope.launch { runCatching { cleanUpCopies() } }
        // Annotations a killed process never got to write into their files.
        applicationScope.launch { runCatching { resumePendingSaves() } }
    }
}
