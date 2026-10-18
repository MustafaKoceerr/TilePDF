package com.mustafakoceerr.tilepdf.feature.reader.components

import com.mustafakoceerr.tilepdf.testing.FakeLazyListLayoutInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageListScrollingTest {

    // The top bar and status bar cover the first 300 px; the navigation bar the last 100.
    private val info = FakeLazyListLayoutInfo(
        visibleItemsInfo = emptyList(),
        beforeContentPadding = 300,
        afterContentPadding = 100,
    )

    @Test
    fun matchUnderTheTopBarIsHidden() {
        assertFalse(info.isUnobscured(top = 120f, height = 40f))
    }

    @Test
    fun matchAboveTheNavigationBarIsShown() {
        assertTrue(info.isUnobscured(top = 1800f, height = 40f))
    }

    @Test
    fun matchUnderTheNavigationBarIsHidden() {
        assertFalse(info.isUnobscured(top = 1880f, height = 40f))
    }
}
