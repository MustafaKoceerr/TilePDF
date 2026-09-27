package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mustafakoceerr.tilepdf.core.geometry.PageLayout
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Test

class VisiblePagesTest {

    private val zoomState = ZoomState(exponentialDecay()).apply { viewportWidth = 1000 }

    @Test
    fun pageLayoutMatchesListRounding() {
        assertEquals(PageLayout(pageLeft = 20, pageWidth = 960), pageLayout(zoomState, 20))

        zoomState.zoomTo(2.5f, Offset(400f, 0f)) // panX = 600
        // List is 2500 wide; padding 20 * 2.5 = 50 each side.
        assertEquals(PageLayout(pageLeft = 50 - 600, pageWidth = 2400), pageLayout(zoomState, 50))
    }

    @Test
    fun convertsItemOffsetsToViewportSpace() {
        val layoutInfo = FakeLayoutInfo(
            // As measured when zoomed: the list itself is wider than the screen.
            viewportSize = IntSize(2500, 2000),
            beforeContentPadding = 20,
            items = listOf(FakeItem(index = 4, offset = -300, size = 3400)),
        )

        val pages = layoutInfo.visiblePages(PageLayout(pageLeft = -550, pageWidth = 2400), viewportWidth = 1000)

        // Item top is at viewport y -300 + 20 = -280.
        assertEquals(1, pages.size)
        assertEquals(4, pages[0].pageIndex)
        assertEquals(PixelRect(550, 280, 1550, 2280), pages[0].visibleRegion)
    }

    private data class FakeItem(
        override val index: Int,
        override val offset: Int,
        override val size: Int,
    ) : LazyListItemInfo {
        override val key: Any get() = index
        override val contentType: Any? get() = null
    }

    private class FakeLayoutInfo(
        override val viewportSize: IntSize,
        override val beforeContentPadding: Int,
        items: List<LazyListItemInfo>,
    ) : LazyListLayoutInfo {
        override val visibleItemsInfo = items
        override val viewportStartOffset get() = -beforeContentPadding
        override val viewportEndOffset get() = viewportSize.height
        override val totalItemsCount get() = 100
        override val orientation get() = Orientation.Vertical
        override val reverseLayout get() = false
        override val afterContentPadding get() = 0
        override val mainAxisItemSpacing get() = 0
    }
}
