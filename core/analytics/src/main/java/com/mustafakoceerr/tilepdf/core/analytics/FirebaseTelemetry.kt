package com.mustafakoceerr.tilepdf.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.FirebaseAnalytics.ConsentStatus
import com.google.firebase.analytics.FirebaseAnalytics.ConsentType
import com.google.firebase.crashlytics.FirebaseCrashlytics

internal class FirebaseAnalyticsHelper(private val analytics: FirebaseAnalytics) : AnalyticsHelper {
    override fun logEvent(event: AnalyticsEvent) {
        analytics.logEvent(event.type, Bundle().apply { event.params.forEach { (key, value) -> putString(key, value) } })
    }
}

internal class FirebaseCrashReporter(private val crashlytics: FirebaseCrashlytics) : CrashReporter {
    override fun recordNonFatal(error: Throwable) = crashlytics.recordException(error)
}

/**
 * @param debugBuild crash reports stay off in debug builds, whatever the
 *   setting says: a developer's crashes would bury real ones.
 */
internal class FirebaseTelemetrySwitches(
    private val analytics: FirebaseAnalytics,
    private val crashlytics: FirebaseCrashlytics,
    private val debugBuild: Boolean,
) : TelemetrySwitches {

    override fun setCrashReportsEnabled(enabled: Boolean) {
        val collect = enabled && !debugBuild
        crashlytics.setCrashlyticsCollectionEnabled(collect)
        // Reports kept while it was off are never sent.
        if (!collect) crashlytics.deleteUnsentReports()
    }

    /** Statistics only: nothing for ads, whatever the user chose. */
    override fun setAnalyticsEnabled(enabled: Boolean) {
        val storage = if (enabled) ConsentStatus.GRANTED else ConsentStatus.DENIED
        analytics.setConsent(
            mapOf(
                ConsentType.ANALYTICS_STORAGE to storage,
                ConsentType.AD_STORAGE to ConsentStatus.DENIED,
                ConsentType.AD_USER_DATA to ConsentStatus.DENIED,
                ConsentType.AD_PERSONALIZATION to ConsentStatus.DENIED,
            ),
        )
        analytics.setAnalyticsCollectionEnabled(enabled)
    }
}

/** Without google-services.json Firebase isn't set up: nothing is sent. */
internal object NoOpTelemetry : AnalyticsHelper, CrashReporter, TelemetrySwitches {
    override fun logEvent(event: AnalyticsEvent) = Unit
    override fun recordNonFatal(error: Throwable) = Unit
    override fun setCrashReportsEnabled(enabled: Boolean) = Unit
    override fun setAnalyticsEnabled(enabled: Boolean) = Unit
}
