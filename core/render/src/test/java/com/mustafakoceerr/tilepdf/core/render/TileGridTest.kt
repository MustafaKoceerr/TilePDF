package com.mustafakoceerr.tilepdf.core.render

import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.geometry.VisiblePage
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import org.junit.Assert.assertEquals
import org.junit.Test

class TileGridTest {

    // 1300 x 1100 -> 3 columns (512, 512, 276) x 3 rows (512, 512, 76).
    private val grid = TileGrid(pageIndex = 2, renderWidth = 1300, renderHeight = 1100)

    @Test
    fun edgeTilesAreCroppedToThePage() {
        assertEquals(3, grid.columns)
        assertEquals(3, grid.rows)
        assertEquals(PixelRect(0, 0, 512, 512), grid.tile(0, 0).bounds)
        assertEquals(PixelRect(1024, 1024, 1300, 1100), grid.tile(2, 2).bounds)
        assertEquals(TileKey(2, 1300, 2, 2), grid.tile(2, 2).key)
    }

    @Test
    fun exactMultipleHasNoEmptyEdgeTile() {
        val exact = TileGrid(0, renderWidth = 1024, renderHeight = 512)
        assertEquals(2, exact.columns)
        assertEquals(1, exact.rows)
    }

    @Test
    fun coversOnlyOverlappingTilesWithoutPrefetch() {
        // Ends exactly on a tile boundary: column 1 must not be included.
        val tiles = grid.tilesCovering(PixelRect(0, 600, 512, 700), prefetch = 0)
        assertEquals(listOf(0 to 1), tiles.map { it.key.col to it.key.row })
    }

    @Test
    fun prefetchWidensAndIsClampedToGrid() {
        val tiles = grid.tilesCovering(PixelRect(0, 0, 100, 100), prefetch = 1)
        assertEquals(
            setOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),
            tiles.map { it.key.col to it.key.row }.toSet(),
        )
    }

    @Test
    fun tilesAreOrderedFromCentreOutwards() {
        val tiles = grid.tilesCovering(PixelRect(600, 600, 700, 700), prefetch = 1)
        assertEquals(9, tiles.size)
        assertEquals(1 to 1, tiles.first().key.let { it.col to it.row })
    }

    @Test
    fun renderLevelNeverFallsBelowZoom() {
        assertEquals(1f, RenderScale.levelFor(1f))
        assertEquals(2f, RenderScale.levelFor(1.6f))
        assertEquals(2f, RenderScale.levelFor(2.005f)) // float noise
        assertEquals(3f, RenderScale.levelFor(2.1f))
        assertEquals(5f, RenderScale.levelFor(5f))
    }

    @Test
    fun visibleRegionIsMappedToRenderPixels() {
        // Laid out at 2.1x (2100 px wide) but rendered at the 3x level.
        val pageSize = PageSize(widthPt = 500, heightPt = 1000)
        val grid = TileGrid.forZoom(0, pageSize, fitWidth = 1000, zoom = 2.1f)
        assertEquals(3000, grid.renderWidth)
        assertEquals(6000, grid.renderHeight)

        val page = VisiblePage(0, pageWidth = 2100, pageHeight = 4200,
            visibleRegion = PixelRect(700, 1400, 1400, 2100))
        // x 1000..2000, y 2000..3000 in render space -> columns 1..3, rows 3..5.
        val tiles = grid.tilesFor(page, prefetch = 0)
        assertEquals(
            (3..5).flatMap { row -> (1..3).map { col -> col to row } }.toSet(),
            tiles.map { it.key.col to it.key.row }.toSet(),
        )
    }
}
