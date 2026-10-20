package com.mustafakoceerr.tilepdf.core.analytics

/** Logs usage statistics; does nothing until the user allows them. */
fun interface AnalyticsHelper {
    fun logEvent(event: AnalyticsEvent)
}

/** Reports problems that didn't crash the app, as crash reports do. */
fun interface CrashReporter {
    fun recordNonFatal(error: Throwable)
}

/** Turns sending on or off, as the user chose. */
interface TelemetrySwitches {
    fun setCrashReportsEnabled(enabled: Boolean)
    fun setAnalyticsEnabled(enabled: Boolean)
}
