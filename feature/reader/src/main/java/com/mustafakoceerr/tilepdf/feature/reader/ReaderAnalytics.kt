package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.analytics.AnalyticsEvent

// The reader's usage statistics. Kinds and ranges only: nothing that could
// tell which document it was.

internal fun pageCountRange(pageCount: Int): String = when {
    pageCount <= 1 -> "1"
    pageCount <= 10 -> "2-10"
    pageCount <= 50 -> "11-50"
    pageCount <= 200 -> "51-200"
    else -> "201+"
}

internal fun documentOpenedEvent(pageCount: Int, hasForm: Boolean, isXfa: Boolean, encrypted: Boolean) = AnalyticsEvent(
    "document_opened",
    mapOf(
        "pages" to pageCountRange(pageCount),
        "form" to when {
            isXfa -> "xfa"
            hasForm -> "acroform"
            else -> "none"
        },
        "encrypted" to encrypted.toString(),
    ),
)

internal fun openFailedEvent(reason: ReaderError) = AnalyticsEvent("document_open_failed", mapOf("reason" to reason.name))

internal fun textRecognitionEvent(allPages: Boolean, result: RecognitionResult) = AnalyticsEvent(
    "text_recognition",
    mapOf(
        "scope" to if (allPages) "all_pages" else "page",
        "result" to when (result) {
            is RecognitionResult.Done -> "done"
            is RecognitionResult.AlreadyHasText -> "already_has_text"
            RecognitionResult.Failed -> "failed"
        },
    ),
)

internal fun annotationAddedEvent(kind: String) = AnalyticsEvent("annotation_added", mapOf("kind" to kind))

/** An action that needs no detail, e.g. "document_shared". */
internal fun readerActionEvent(type: String) = AnalyticsEvent(type)
