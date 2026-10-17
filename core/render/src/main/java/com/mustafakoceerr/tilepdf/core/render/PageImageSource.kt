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

    /**
     * Drops off-screen cached images when the system is low on memory.
     *
     * @param uiHidden the app isn't visible, so no frame can still be drawing
     *   the dropped bitmaps and their native memory can be freed right away
     *   instead of waiting for a garbage collection.
     */
    fun trimMemory(uiHidden: Boolean)

    /**
     * The page's content changed (an annotation was hidden or shown): its
     * draft and tiles are rendered again; what's on screen stays until then.
     */
    fun invalidate(pageIndex: Int)

    /** Changes each time [pageIndex] is invalidated; observable from composition. */
    fun version(pageIndex: Int): Int
}

class RenderedTile(val tile: Tile, val image: ImageBitmap)
