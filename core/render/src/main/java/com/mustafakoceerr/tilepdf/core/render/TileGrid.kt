package com.mustafakoceerr.tilepdf.core.render

import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.geometry.VisiblePage
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import kotlin.math.ceil
import kotlin.math.floor

const val TILE_SIZE = 512

/**
 * Identifies one tile. [renderWidth] is the page width the tile was rendered
 * for, so tiles of different zoom levels never collide. The cache is scoped to
 * one document, so no document id is needed.
 */
data class TileKey(val pageIndex: Int, val renderWidth: Int, val col: Int, val row: Int)

/** A tile and its bounds in the page's render pixel space. */
data class Tile(val key: TileKey, val bounds: PixelRect)

/**
 * Splits a page rendered [renderWidth] x [renderHeight] pixels into a grid of
 * [TILE_SIZE] squares. Tiles in the last column/row are cropped to the page.
 */
class TileGrid(
    val pageIndex: Int,
    val renderWidth: Int,
    val renderHeight: Int,
) {
    val columns: Int = ceilDiv(renderWidth, TILE_SIZE)
    val rows: Int = ceilDiv(renderHeight, TILE_SIZE)

    fun tile(col: Int, row: Int): Tile {
        val left = col * TILE_SIZE
        val top = row * TILE_SIZE
        return Tile(
            key = TileKey(pageIndex, renderWidth, col, row),
            bounds = PixelRect(
                left = left,
                top = top,
                right = minOf(left + TILE_SIZE, renderWidth),
                bottom = minOf(top + TILE_SIZE, renderHeight),
            ),
        )
    }

    /**
     * Tiles overlapping [region] (render pixel space), widened by [prefetch]
     * tiles on every side, ordered from the region's centre outwards so the
     * part the user is looking at renders first.
     */
    fun tilesCovering(region: PixelRect, prefetch: Int = 1): List<Tile> {
        val firstCol = (region.left / TILE_SIZE - prefetch).coerceAtLeast(0)
        val firstRow = (region.top / TILE_SIZE - prefetch).coerceAtLeast(0)
        val lastCol = ((region.right - 1) / TILE_SIZE + prefetch).coerceAtMost(columns - 1)
        val lastRow = ((region.bottom - 1) / TILE_SIZE + prefetch).coerceAtMost(rows - 1)

        val centerX = (region.left + region.right) / 2f
        val centerY = (region.top + region.bottom) / 2f
        return buildList {
            for (row in firstRow..lastRow) {
                for (col in firstCol..lastCol) add(tile(col, row))
            }
        }.sortedBy { tile ->
            val dx = (tile.bounds.left + tile.bounds.right) / 2f - centerX
            val dy = (tile.bounds.top + tile.bounds.bottom) / 2f - centerY
            dx * dx + dy * dy
        }
    }

    companion object {

        /**
         * Grid for a page of [pageSize] at the render level matching [zoom]. [fitWidth] is
         * the page's laid out width at 1x.
         */
        fun forZoom(pageIndex: Int, pageSize: PageSize, fitWidth: Int, zoom: Float): TileGrid {
            val renderWidth = (fitWidth * RenderScale.levelFor(zoom)).toInt()
            return TileGrid(pageIndex, renderWidth, pageSize.heightForWidth(renderWidth))
        }
    }
}

/**
 * Tiles needed to show [page]. The visible region is in laid out pixels, which
 * differ from render pixels whenever the zoom sits between render levels.
 */
fun TileGrid.tilesFor(page: VisiblePage, prefetch: Int = 1): List<Tile> {
    val toRender = renderWidth.toFloat() / page.pageWidth
    val region = page.visibleRegion
    val renderRegion = PixelRect(
        left = floor(region.left * toRender).toInt(),
        top = floor(region.top * toRender).toInt(),
        right = ceil(region.right * toRender).toInt().coerceAtMost(renderWidth),
        bottom = ceil(region.bottom * toRender).toInt().coerceAtMost(renderHeight),
    )
    return tilesCovering(renderRegion, prefetch)
}

private fun ceilDiv(a: Int, b: Int) = (a + b - 1) / b
