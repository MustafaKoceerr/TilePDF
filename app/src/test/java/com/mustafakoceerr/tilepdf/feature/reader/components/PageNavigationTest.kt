package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.unit.IntSize
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
        val info = FakeLayoutInfo(
            viewportHeight = 2000,
            items = listOf(Item(11, offset = -1100, size = 1400), Item(12, offset = 316, size = 1400)),
        )
        assertEquals(12, info.centerPage())
    }

    @Test
    fun centerPageFallsBackToFirstVisibleWhenMiddleIsInAGap() {
        val info = FakeLayoutInfo(
            viewportHeight = 1000,
            items = listOf(Item(3, offset = 0, size = 400), Item(4, offset = 700, size = 400)),
        )
        assertEquals(3, info.centerPage())
    }

    private data class Item(
        override val index: Int,
        override val offset: Int,
        override val size: Int,
    ) : LazyListItemInfo {
        override val key: Any get() = index
        override val contentType: Any? get() = null
    }

    private class FakeLayoutInfo(viewportHeight: Int, items: List<LazyListItemInfo>) : LazyListLayoutInfo {
        override val visibleItemsInfo = items
        override val viewportStartOffset = 0
        override val viewportEndOffset = viewportHeight
        override val viewportSize = IntSize(1000, viewportHeight)
        override val totalItemsCount = 300
        override val orientation = Orientation.Vertical
        override val reverseLayout = false
        override val beforeContentPadding = 0
        override val afterContentPadding = 0
        override val mainAxisItemSpacing = 16
    }
}
