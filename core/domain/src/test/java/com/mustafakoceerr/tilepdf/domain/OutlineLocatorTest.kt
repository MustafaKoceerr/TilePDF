package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.model.OutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutlineLocatorTest {

    private val outline = listOf(
        OutlineEntry("1. Intro", pageIndex = 0, topPt = 100f, depth = 0),
        OutlineEntry("1.1 Motivation", 0, 442f, 1),
        OutlineEntry("2. Engine", 3, null, 0),
        OutlineEntry("2.1 Tiles", 4, 242f, 1),
        OutlineEntry("3. Search", 8, 542f, 0),
    )

    private fun current(page: Int, y: Float) = OutlineLocator.currentIndex(outline, page, y)?.let { outline[it].title }

    @Test
    fun beforeTheFirstHeadingThereIsNoCurrentEntry() {
        assertNull(current(page = 0, y = 50f))
    }

    @Test
    fun headingCountsOnceItReachesTheTopOfTheScreen() {
        assertEquals("1. Intro", current(0, 100f))
        assertEquals("1. Intro", current(0, 441f))
        assertEquals("1.1 Motivation", current(0, 442f))
    }

    @Test
    fun laterPagesKeepTheLastHeadingBeforeThem() {
        assertEquals("1.1 Motivation", current(2, 800f))
        assertEquals("2. Engine", current(3, 0f)) // no y: top of the page
        assertEquals("2. Engine", current(4, 200f))
        assertEquals("2.1 Tiles", current(7, 0f))
        assertEquals("3. Search", current(11, 0f))
    }

    @Test
    fun outOfOrderOutlinesStillResolveByPosition() {
        val shuffled = outline.shuffled(kotlin.random.Random(7))
        val index = OutlineLocator.currentIndex(shuffled, pageIndex = 5, yPt = 0f)!!
        assertEquals("2.1 Tiles", shuffled[index].title)
    }

    @Test
    fun onATieTheLaterEntryWins() {
        val tied = listOf(
            OutlineEntry("Chapter", 2, null, 0),
            OutlineEntry("First section", 2, null, 1),
        )
        assertEquals(1, OutlineLocator.currentIndex(tied, 2, 10f))
    }
}
