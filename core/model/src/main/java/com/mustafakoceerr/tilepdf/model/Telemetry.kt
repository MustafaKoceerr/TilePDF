package com.mustafakoceerr.tilepdf.model

/** Whether the user agreed to anonymous usage statistics. Stored by name. */
enum class AnalyticsConsent { Unasked, Granted, Denied }

/** What TilePDF may send: crash reports unless turned off, statistics only once allowed. */
data class TelemetryPreferences(
    val crashReportsEnabled: Boolean = true,
    val analyticsConsent: AnalyticsConsent = AnalyticsConsent.Unasked,
)
