package com.mustafakoceerr.tilepdf.feature.reader.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SpreadsTest {

    @Test
    fun coverStandsAloneThenPagesPair() =
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), spreads(pageCount = 5))

    @Test
    fun lastPageMayStandAlone() =
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), spreads(pageCount = 4))

    @Test
    fun singlePageIsOneSpread() = assertEquals(listOf(listOf(0)), spreads(pageCount = 1))

    @Test
    fun twoPagesAreCoverAndBack() = assertEquals(listOf(listOf(0), listOf(1)), spreads(pageCount = 2))

    @Test
    fun noPagesNoSpreads() = assertEquals(emptyList<List<Int>>(), spreads(pageCount = 0))

    @Test
    fun pageFindsItsSpread() {
        val spreads = spreads(pageCount = 5)

        assertEquals(listOf(0, 1, 1, 2, 2), (0 until 5).map { spreadOf(it, spreads) })
    }

    @Test
    fun pageOutsideTheDocumentFindsTheNearestEnd() {
        val spreads = spreads(pageCount = 5)

        assertEquals(2, spreadOf(9, spreads))
        assertEquals(0, spreadOf(-1, spreads))
    }

    @Test
    fun spreadsNeedAWideLandscapeSpace() {
        assertEquals(true, showsSpreads(enabled = true, widthDp = 1280f, heightDp = 800f))
        assertEquals(false, showsSpreads(enabled = false, widthDp = 1280f, heightDp = 800f))
        assertEquals(false, showsSpreads(enabled = true, widthDp = 800f, heightDp = 400f))
        assertEquals(false, showsSpreads(enabled = true, widthDp = 900f, heightDp = 1200f))
    }
}
