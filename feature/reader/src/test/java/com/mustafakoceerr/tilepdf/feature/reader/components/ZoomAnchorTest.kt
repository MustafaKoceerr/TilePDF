package com.mustafakoceerr.tilepdf.feature.reader.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The list's top padding: item offsets start 100 px below the screen's top. */
private const val START = -100

class ZoomAnchorTest {

    // Two 1000 px pages with a 16 px gap; page 3 starts 100 px above the screen.
    private val items = listOf(
        ListItemBounds(index = 3, offset = -200, size = 1000),
        ListItemBounds(index = 4, offset = 816, size = 1000),
    )

    /** Where the anchored content lands on screen, given the new item size. */
    private fun ScrollTarget.screenY(fraction: Float, newSize: Float) = -scrollOffset - START + fraction * newSize

    @Test
    fun zoomingInKeepsTheContentUnderTheFingers() {
        // Screen y 400 shows page 3 half way down (item offset 300).
        val target = anchoredScroll(items, START, ZoomCommit(zoom = 2f, focusY = 400f, contentY = 400f))!!

        assertEquals(3, target.index)
        assertEquals(400f, target.screenY(0.5f, 2000f), 1f)
    }

    @Test
    fun pinchingOutKeepsTheContentUnderTheFingers() {
        // Pinched out, screen y 600 shows what was at screen y 1300: page 4, 38.4% down.
        val target = anchoredScroll(items, START, ZoomCommit(zoom = 0.5f, focusY = 600f, contentY = 1300f))!!

        assertEquals(4, target.index)
        assertEquals(600f, target.screenY(0.384f, 500f), 1f)
    }

    @Test
    fun anAnchorBetweenPagesHoldsOnToTheNextPage() {
        // Item offset 808 is in the gap; page 4's top goes to the fingers.
        val target = anchoredScroll(items, START, ZoomCommit(zoom = 2f, focusY = 500f, contentY = 908f))!!

        assertEquals(4, target.index)
        assertEquals(500f, target.screenY(0f, 2000f), 1f)
    }

    @Test
    fun anAnchorBelowTheLastPageHoldsOnToItsBottom() {
        val target = anchoredScroll(items, START, ZoomCommit(zoom = 2f, focusY = 1900f, contentY = 2000f))!!

        assertEquals(4, target.index)
        assertEquals(1900f, target.screenY(1f, 2000f), 1f)
    }

    @Test
    fun nothingLaidOutMeansNoScroll() {
        assertNull(anchoredScroll(emptyList(), START, ZoomCommit(zoom = 2f, focusY = 0f, contentY = 0f)))
    }
}
