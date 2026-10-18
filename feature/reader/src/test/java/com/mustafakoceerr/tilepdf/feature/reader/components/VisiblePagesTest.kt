package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.animation.core.exponentialDecay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mustafakoceerr.tilepdf.core.geometry.PageLayout
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.testing.FakeLazyListLayoutInfo
import com.mustafakoceerr.tilepdf.testing.FakeListItem
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
        val layoutInfo = FakeLazyListLayoutInfo(
            visibleItemsInfo = listOf(FakeListItem(index = 4, offset = -300, size = 3400)),
            // As measured when zoomed: the list itself is wider than the screen.
            viewportSize = IntSize(2500, 2000),
            beforeContentPadding = 20,
        )

        val pages = layoutInfo.visiblePages(PageLayout(pageLeft = -550, pageWidth = 2400), viewportWidth = 1000)

        // Item top is at viewport y -300 + 20 = -280.
        assertEquals(1, pages.size)
        assertEquals(4, pages[0].pageIndex)
        assertEquals(PixelRect(550, 280, 1550, 2280), pages[0].visibleRegion)
    }
}
