package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.model.ReadingPosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingModeSwitchTest {

    @Test
    fun mostlyVisibleTopPageIsKept() {
        assertEquals(ReadingPosition(4), positionForModeSwitch(ReadingPosition(4, pageOffsetFraction = 0.3f), pageCount = 12))
    }

    @Test
    fun topPageMostlyScrolledAwayGivesNextPage() {
        // Only a sliver of page 5 shows above page 6.
        assertEquals(ReadingPosition(5), positionForModeSwitch(ReadingPosition(4, pageOffsetFraction = 0.9f), pageCount = 12))
    }

    @Test
    fun lastPageStaysLast() {
        assertEquals(ReadingPosition(11), positionForModeSwitch(ReadingPosition(11, pageOffsetFraction = 0.8f), pageCount = 12))
    }

    @Test
    fun zoomAndPanAreDropped() {
        val position = ReadingPosition(2, pageOffsetFraction = 0.1f, zoom = 3f, panFraction = 0.5f)
        assertEquals(ReadingPosition(2), positionForModeSwitch(position, pageCount = 12))
    }
}
