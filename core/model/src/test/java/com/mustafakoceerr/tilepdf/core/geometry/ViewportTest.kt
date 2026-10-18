package com.mustafakoceerr.tilepdf.core.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ViewportTest {

    private val viewport = Viewport(width = 1000, height = 2000)

    @Test
    fun fullyVisiblePage() {
        val page = viewport.visiblePage(0, PixelRect.ofSize(20, 100, 960, 1360))!!

        assertEquals(960, page.pageWidth)
        assertEquals(1360, page.pageHeight)
        assertEquals(PixelRect(0, 0, 960, 1360), page.visibleRegion)
    }

    @Test
    fun pageCutOffAtTopIsReportedInPageSpace() {
        val page = viewport.visiblePage(3, PixelRect.ofSize(20, -500, 960, 1360))!!

        assertEquals(3, page.pageIndex)
        assertEquals(PixelRect(0, 500, 960, 1360), page.visibleRegion)
    }

    @Test
    fun pageCutOffAtBottom() {
        val page = viewport.visiblePage(0, PixelRect.ofSize(20, 1500, 960, 1360))!!

        assertEquals(PixelRect(0, 0, 960, 500), page.visibleRegion)
    }

    @Test
    fun zoomedAndPannedPageIsClippedOnBothSides() {
        // 3x zoom, panned so the page starts 1200 px left of the screen.
        val layout = PageLayout(pageLeft = -1200, pageWidth = 2880)
        val page = viewport.visiblePage(0, layout.pageBounds(top = -1000, height = 4080))!!

        assertEquals(PixelRect(1200, 1000, 2200, 3000), page.visibleRegion)
    }

    @Test
    fun offScreenPageIsNull() {
        assertNull(viewport.visiblePage(0, PixelRect.ofSize(0, 2000, 960, 1360)))
        assertNull(viewport.visiblePage(0, PixelRect.ofSize(-960, 0, 960, 1360)))
    }
}
