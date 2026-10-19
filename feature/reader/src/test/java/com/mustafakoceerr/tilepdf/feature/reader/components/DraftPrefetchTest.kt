package com.mustafakoceerr.tilepdf.feature.reader.components

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftPrefetchTest {

    @Test
    fun theTwoPagesOnEachSideOfTheScreenAreFetchedNearestFirst() {
        // Zooming out reveals the pages right next to the screen first.
        assertEquals(listOf(4, 7, 3, 8), draftPrefetchPages(visible = 5..6, pageCount = 80))
    }

    @Test
    fun pagesPastTheDocumentEndsAreSkipped() {
        assertEquals(listOf(1, 2), draftPrefetchPages(visible = 0..0, pageCount = 3))
        assertEquals(listOf(78, 77), draftPrefetchPages(visible = 79..79, pageCount = 80))
    }
}
