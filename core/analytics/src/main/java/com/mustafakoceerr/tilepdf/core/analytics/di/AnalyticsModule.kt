package com.mustafakoceerr.tilepdf.core.analytics.di

import android.content.Context
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.mustafakoceerr.tilepdf.core.analytics.AnalyticsHelper
import com.mustafakoceerr.tilepdf.core.analytics.CrashReporter
import com.mustafakoceerr.tilepdf.core.analytics.FirebaseAnalyticsHelper
import com.mustafakoceerr.tilepdf.core.analytics.FirebaseCrashReporter
import com.mustafakoceerr.tilepdf.core.analytics.FirebaseTelemetrySwitches
import com.mustafakoceerr.tilepdf.core.analytics.NoOpTelemetry
import com.mustafakoceerr.tilepdf.core.analytics.TelemetrySwitches
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {

    /** Firebase is set up only in builds made with google-services.json. */
    private fun Context.hasFirebase() = FirebaseApp.getApps(this).isNotEmpty()

    @Provides
    @Singleton
    fun providesAnalyticsHelper(@ApplicationContext context: Context): AnalyticsHelper =
        if (context.hasFirebase()) FirebaseAnalyticsHelper(FirebaseAnalytics.getInstance(context)) else NoOpTelemetry

    @Provides
    @Singleton
    fun providesCrashReporter(@ApplicationContext context: Context): CrashReporter =
        if (context.hasFirebase()) FirebaseCrashReporter(FirebaseCrashlytics.getInstance()) else NoOpTelemetry

    @Provides
    @Singleton
    fun providesTelemetrySwitches(@ApplicationContext context: Context): TelemetrySwitches =
        if (context.hasFirebase()) {
            FirebaseTelemetrySwitches(
                FirebaseAnalytics.getInstance(context),
                FirebaseCrashlytics.getInstance(),
                debugBuild = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            )
        } else {
            NoOpTelemetry
        }
}
