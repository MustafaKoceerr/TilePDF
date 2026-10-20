package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.analytics.AnalyticsEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderAnalyticsTest {

    @Test
    fun pageCountsGoIntoRangesNotExactNumbers() {
        assertEquals(
            listOf("1", "2-10", "2-10", "11-50", "51-200", "201+"),
            listOf(1, 2, 10, 11, 200, 201).map(::pageCountRange),
        )
    }

    @Test
    fun anOpenedDocumentIsDescribedByKindOnly() {
        assertEquals(
            AnalyticsEvent("document_opened", mapOf("pages" to "11-50", "form" to "xfa", "encrypted" to "true")),
            documentOpenedEvent(pageCount = 12, hasForm = true, isXfa = true, encrypted = true),
        )
        assertEquals(
            "acroform",
            documentOpenedEvent(pageCount = 1, hasForm = true, isXfa = false, encrypted = false).params["form"],
        )
        assertEquals(
            "none",
            documentOpenedEvent(pageCount = 1, hasForm = false, isXfa = false, encrypted = false).params["form"],
        )
    }

    @Test
    fun aFailedOpenNamesTheReason() {
        assertEquals(
            AnalyticsEvent("document_open_failed", mapOf("reason" to "Corrupt")),
            openFailedEvent(ReaderError.Corrupt),
        )
    }

    @Test
    fun textRecognitionNamesScopeAndOutcomeWithoutCounts() {
        assertEquals(
            AnalyticsEvent("text_recognition", mapOf("scope" to "all_pages", "result" to "done")),
            textRecognitionEvent(allPages = true, RecognitionResult.Done(pagesWithText = 7)),
        )
        assertEquals("already_has_text", textRecognitionEvent(false, RecognitionResult.AlreadyHasText(false)).params["result"])
        assertEquals("failed", textRecognitionEvent(false, RecognitionResult.Failed).params["result"])
        assertEquals("page", textRecognitionEvent(false, RecognitionResult.Failed).params["scope"])
    }
}
