package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.unit.IntSize
import com.mustafakoceerr.tilepdf.testing.FakeLazyListLayoutInfo
import com.mustafakoceerr.tilepdf.testing.FakeListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PageNavigationTest {

    @Test
    fun pageNumbersAreValidatedAndZeroBased() {
        assertEquals(0, parsePageNumber("1", 300))
        assertEquals(299, parsePageNumber(" 300 ", 300))
        assertNull(parsePageNumber("0", 300))
        assertNull(parsePageNumber("301", 300))
        assertNull(parsePageNumber("", 300))
        assertNull(parsePageNumber("12a", 300))
    }

    @Test
    fun progressMapsToPagesIncludingBothEnds() {
        assertEquals(0, pageForProgress(0f, 300))
        assertEquals(299, pageForProgress(1f, 300))
        assertEquals(150, pageForProgress(0.5f, 301))
        assertEquals(0, pageForProgress(-0.2f, 300))
    }

    @Test
    fun centerPageIsThePageUnderTheViewportMiddle() {
        // Viewport 2000 tall: page 11 shows its last 300 px, page 12 fills the middle.
        val info = FakeLazyListLayoutInfo(
            visibleItemsInfo = listOf(
                FakeListItem(11, offset = -1100, size = 1400),
                FakeListItem(12, offset = 316, size = 1400),
            ),
            viewportSize = IntSize(1000, 2000),
            mainAxisItemSpacing = 16,
        )
        assertEquals(12, info.centerPage())
    }

    @Test
    fun centerPageFallsBackToFirstVisibleWhenMiddleIsInAGap() {
        val info = FakeLazyListLayoutInfo(
            visibleItemsInfo = listOf(
                FakeListItem(3, offset = 0, size = 400),
                FakeListItem(4, offset = 700, size = 400),
            ),
            viewportSize = IntSize(1000, 1000),
        )
        assertEquals(3, info.centerPage())
    }
}
