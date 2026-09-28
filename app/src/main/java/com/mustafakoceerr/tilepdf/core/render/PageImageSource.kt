package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Supplies rendered page images to the UI in two layers:
 * - a low-resolution **draft** of the whole page, shown immediately;
 * - sharp **tiles** for the visible part, drawn over the draft as they arrive.
 */
@Stable
interface PageImageSource {

    /** A draft already in memory, returned without suspending. */
    fun cachedDraft(pageIndex: Int, widthPx: Int): Bitmap?

    suspend fun loadDraft(pageIndex: Int, widthPx: Int): Bitmap

    /**
     * Declares the tiles needed on screen right now. Missing ones are
     * rendered in the given order; pending work for anything else is cancelled.
     */
    fun requestTiles(tiles: List<Tile>)

    /**
     * Tiles of [pageIndex] ready to draw. Backed by snapshot state, so reading
     * it while drawing redraws the page as tiles arrive.
     */
    fun renderedTiles(pageIndex: Int): List<RenderedTile>

    /** Drops off-screen cached images when the system is low on memory. */
    fun trimMemory()
}

class RenderedTile(val tile: Tile, val image: ImageBitmap)
