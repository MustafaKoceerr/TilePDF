package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.model.ReadingPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JumpHistoryTest {

    private val page3 = ReadingPosition(pageIndex = 3, pageOffsetFraction = 0.4f)

    @Test
    fun backReturnsOrigin() {
        val history = JumpHistory().jumped(from = page3, to = 20)

        assertEquals(page3, history.origin)
        assertEquals(20, history.target)
    }

    @Test
    fun samePageJumpIsIgnored() {
        val history = JumpHistory().jumped(from = page3, to = 3)

        assertNull(history.origin)
    }

    @Test
    fun samePageJumpKeepsEarlierOrigin() {
        val history = JumpHistory().jumped(from = page3, to = 20).jumped(from = ReadingPosition(20), to = 20)

        assertEquals(page3, history.origin)
    }

    @Test
    fun newJumpReplacesOrigin() {
        val chapter = ReadingPosition(pageIndex = 20)
        val history = JumpHistory().jumped(from = page3, to = 20).jumped(from = chapter, to = 40)

        assertEquals(chapter, history.origin)
        assertEquals(40, history.target)
    }

    @Test
    fun readingNearTargetKeepsIt() {
        val history = JumpHistory().jumped(from = page3, to = 20)

        assertEquals(page3, history.scrolledTo(23).origin)
        assertEquals(page3, history.scrolledTo(17).origin)
    }

    @Test
    fun scrollingFarAwayForgetsIt() {
        val history = JumpHistory().jumped(from = page3, to = 20)

        assertNull(history.scrolledTo(24).origin)
        assertNull(history.scrolledTo(16).origin)
    }
}
