package com.mustafakoceerr.tilepdf.core.analytics

/**
 * Something that happened in the app, for usage statistics. Only categories
 * and counts go in: never file names, paths, document text or anything else
 * a user typed or opened.
 */
data class AnalyticsEvent(
    val type: String,
    val params: Map<String, String> = emptyMap(),
) {
    companion object {
        const val SCREEN_VIEW = "screen_view"
        const val SCREEN_NAME = "screen_name"

        fun screenView(screenName: String) = AnalyticsEvent(SCREEN_VIEW, mapOf(SCREEN_NAME to screenName))
    }
}
