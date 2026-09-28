package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.asImageBitmap
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Budget for low-resolution whole-page drafts. */
private const val DRAFT_CACHE_BYTES = 24 * 1024 * 1024

/** Upper bound for tiles; a full screen at 5x needs roughly 40 MB. */
private const val MAX_TILE_CACHE_BYTES = 96 * 1024 * 1024

/**
 * [PageImageSource] for one open document.
 *
 * Memory is bounded twice: [tileCache] is an LRU cache of recently rendered
 * tiles (so scrolling back is instant), while [liveTiles] only holds what is
 * on screen now. Tiles leaving the screen are dropped from [liveTiles] and
 * their pending renders cancelled, so a fast fling never queues up work for
 * pages the user has already passed.
 *
 * Must be used from the main thread; renders run inside [scope] and hop to
 * the PDF thread only for the actual rendering.
 */
class TiledPageImageSource(
    private val document: PdfDocument,
    private val pageRenderer: PageRenderer,
    private val scope: CoroutineScope,
    tileCacheBytes: Int = defaultTileCacheBytes(),
) : PageImageSource {

    private val draftCache = BitmapCache<Int>(DRAFT_CACHE_BYTES)
    private val tileCache = BitmapCache<TileKey>(tileCacheBytes)
    private val liveTiles = mutableStateMapOf<TileKey, RenderedTile>()
    private val pendingRenders = HashMap<TileKey, Job>()

    override fun cachedDraft(pageIndex: Int, widthPx: Int): Bitmap? =
        draftCache.get(pageIndex)?.takeIf { it.width == widthPx }

    override suspend fun loadDraft(pageIndex: Int, widthPx: Int): Bitmap =
        cachedDraft(pageIndex, widthPx)
            ?: pageRenderer.renderPage(document, pageIndex, widthPx)
                .also { draftCache.put(pageIndex, it) }

    override fun requestTiles(tiles: List<Tile>) {
        val wanted = tiles.mapTo(HashSet()) { it.key }
        pendingRenders.entries.removeAll { (key, job) ->
            (key !in wanted).also { stale -> if (stale) job.cancel() }
        }
        liveTiles.keys.retainAll(wanted)

        for (tile in tiles) {
            val key = tile.key
            if (key in liveTiles || key in pendingRenders) continue
            val cached = tileCache.get(key)
            if (cached != null) {
                liveTiles[key] = RenderedTile(tile, cached.asImageBitmap())
                continue
            }
            pendingRenders[key] = scope.launch {
                val bitmap = pageRenderer.renderTile(document, tile)
                tileCache.put(key, bitmap)
                liveTiles[key] = RenderedTile(tile, bitmap.asImageBitmap())
                pendingRenders.remove(key)
            }
        }
    }

    override fun renderedTiles(pageIndex: Int): List<RenderedTile> =
        liveTiles.values.filter { it.tile.key.pageIndex == pageIndex }

    override fun trimMemory() {
        tileCache.evictAll()
        draftCache.trimToSize(DRAFT_CACHE_BYTES / 2)
    }

    companion object {
        fun defaultTileCacheBytes(): Int =
            minOf(Runtime.getRuntime().maxMemory() / 4, MAX_TILE_CACHE_BYTES.toLong()).toInt()
    }
}
