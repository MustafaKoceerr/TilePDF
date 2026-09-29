package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Budget for low-resolution whole-page drafts. */
private const val DRAFT_CACHE_BYTES = 24 * 1024 * 1024

/** Upper bound for tiles; a full screen at 5x needs roughly 40 MB. */
private const val MAX_TILE_CACHE_BYTES = 96 * 1024 * 1024

/** Spare bitmaps kept for reuse; 16 full-size tiles. */
private const val BITMAP_POOL_BYTES = 16 * TILE_SIZE * TILE_SIZE * 4

/**
 * [PageImageSource] for one open document.
 *
 * Tiles live in three places:
 * - [tileCache] — LRU cache of recently rendered tiles, so scrolling back or
 *   returning to a zoom level is instant.
 * - [liveTiles] — the tiles last requested, i.e. what should be on screen.
 * - [fallbackTiles] — the previous zoom level's tiles of pages still on
 *   screen. They're drawn under [liveTiles] until the new level covers the
 *   page, so a zoom change never drops back to the blurry draft.
 *
 * Bitmaps evicted from [tileCache] go to [bitmapPool] for reuse, but only
 * once nothing draws them any more.
 *
 * Must be used from the main thread; renders run inside [scope] and hop to
 * the PDF thread only for the actual rendering.
 */
class TiledPageImageSource(
    private val document: PdfDocument,
    private val pageRenderer: PageRenderer,
    private val scope: CoroutineScope,
    tileCacheBytes: Int = defaultTileCacheBytes(),
    @get:VisibleForTesting internal val bitmapPool: BitmapPool = BitmapPool(BITMAP_POOL_BYTES),
) : PageImageSource {

    private val liveTiles = mutableStateMapOf<TileKey, RenderedTile>()
    private val fallbackTiles = mutableStateMapOf<TileKey, RenderedTile>()
    private val pendingRenders = HashMap<TileKey, Job>()

    /** Evicted from the cache while still drawn; pooled once taken off screen. */
    private val evictedWhileShown = HashSet<TileKey>()

    /** Render width each on-screen page was last requested at. */
    private var requestedWidths: Map<Int, Int> = emptyMap()

    /** Tiles each on-screen page needs, from the latest request. */
    private var requestedTiles: Map<Int, List<TileKey>> = emptyMap()

    private val draftCache = BitmapCache<Int>(DRAFT_CACHE_BYTES)
    /** Set while trimming for a hidden UI: evicted bitmaps are freed, not pooled. */
    private var recycleEvicted = false

    private val tileCache = BitmapCache<TileKey>(tileCacheBytes) { key, bitmap ->
        when {
            key in liveTiles || key in fallbackTiles -> evictedWhileShown += key
            recycleEvicted -> bitmap.recycle()
            else -> bitmapPool.release(bitmap)
        }
    }

    override fun cachedDraft(pageIndex: Int, widthPx: Int): Bitmap? =
        draftCache.get(pageIndex)?.takeIf { it.width == widthPx }

    override suspend fun loadDraft(pageIndex: Int, widthPx: Int): Bitmap =
        cachedDraft(pageIndex, widthPx)
            ?: pageRenderer.renderPage(document, pageIndex, widthPx)
                .also { draftCache.put(pageIndex, it) }

    override fun requestTiles(tiles: List<Tile>) {
        val wanted = tiles.mapTo(HashSet()) { it.key }
        requestedTiles = tiles.groupBy({ it.key.pageIndex }, { it.key })
        requestedWidths = requestedTiles.mapValues { (_, keys) -> keys.first().renderWidth }

        pendingRenders.entries.removeAll { (key, job) ->
            (key !in wanted).also { stale -> if (stale) job.cancel() }
        }

        // Zoomed back to a level whose tiles are still the fallback: reuse them.
        fallbackTiles.keys.filter { it in wanted }.forEach { key ->
            liveTiles[key] = fallbackTiles.remove(key)!!
        }

        // Tiles no longer wanted become the fallback if their page is still on
        // screen at another level; the newest level replaces an older fallback.
        val leaving = liveTiles.keys.filter { it !in wanted }
        val pagesWithNewFallback = leaving.filter(::isOtherLevelOfVisiblePage)
            .mapTo(HashSet()) { it.pageIndex }
        fallbackTiles.keys
            .filter { !isOtherLevelOfVisiblePage(it) || it.pageIndex in pagesWithNewFallback }
            .forEach { discard(fallbackTiles, it) }
        for (key in leaving) {
            if (isOtherLevelOfVisiblePage(key)) fallbackTiles[key] = liveTiles.remove(key)!!
            else discard(liveTiles, key)
        }

        for (tile in tiles) {
            val key = tile.key
            if (key in liveTiles || key in pendingRenders) continue
            val cached = tileCache.get(key)
            if (cached != null) {
                liveTiles[key] = RenderedTile(tile, cached.asImageBitmap())
            } else {
                // Registered before starting: a render can finish without ever
                // suspending, and it must find its own entry to remove.
                val job = render(tile)
                pendingRenders[key] = job
                job.start()
            }
        }
        requestedTiles.keys.forEach(::dropFallbackIfCovered)
    }

    override fun renderedTiles(pageIndex: Int): List<RenderedTile> = buildList {
        // Fallback first, so the current level is drawn on top of it.
        fallbackTiles.values.filterTo(this) { it.tile.key.pageIndex == pageIndex }
        liveTiles.values.filterTo(this) { it.tile.key.pageIndex == pageIndex }
    }

    override fun trimMemory(uiHidden: Boolean) {
        // Tiles still on screen are never recycled: they're drawn again when
        // the reader comes back. Drafts aren't either; visible pages show them.
        recycleEvicted = uiHidden
        try {
            tileCache.evictAll()
        } finally {
            recycleEvicted = false
        }
        bitmapPool.clear(recycle = uiHidden)
        draftCache.trimToSize(DRAFT_CACHE_BYTES / 2)
    }

    @VisibleForTesting
    internal suspend fun awaitRenders() = pendingRenders.values.toList().joinAll()

    @get:VisibleForTesting
    internal val pendingRenderCount: Int get() = pendingRenders.size

    private fun render(tile: Tile): Job = scope.launch(start = CoroutineStart.LAZY) {
        // Acquired inside the job: a job cancelled before it starts must not
        // take a bitmap out of the pool.
        val reuse = bitmapPool.acquire(tile.bounds.width * tile.bounds.height * BYTES_PER_PIXEL)
        val bitmap = try {
            pageRenderer.renderTile(document, tile, reuse)
        } catch (e: CancellationException) {
            // The render itself always runs to completion before the
            // cancellation surfaces, so the bitmap is free again.
            reuse?.let(bitmapPool::release)
            throw e
        }
        pendingRenders.remove(tile.key)
        // On screen before cached: if the cache evicts it straight away, it
        // must count as shown rather than go to the pool while being drawn.
        liveTiles[tile.key] = RenderedTile(tile, bitmap.asImageBitmap())
        tileCache.put(tile.key, bitmap)
        dropFallbackIfCovered(tile.key.pageIndex)
    }

    private fun isOtherLevelOfVisiblePage(key: TileKey): Boolean {
        val width = requestedWidths[key.pageIndex] ?: return false
        return width != key.renderWidth
    }

    private fun dropFallbackIfCovered(pageIndex: Int) {
        val needed = requestedTiles[pageIndex] ?: return
        if (needed.all { it in liveTiles }) {
            fallbackTiles.keys.filter { it.pageIndex == pageIndex }
                .forEach { discard(fallbackTiles, it) }
        }
    }

    /** Takes a tile off screen; pools its bitmap if the cache already let go of it. */
    private fun discard(from: SnapshotStateMap<TileKey, RenderedTile>, key: TileKey) {
        val removed = from.remove(key) ?: return
        if (evictedWhileShown.remove(key)) bitmapPool.release(removed.image.asAndroidBitmap())
    }

    companion object {
        private const val BYTES_PER_PIXEL = 4

        fun defaultTileCacheBytes(): Int =
            minOf(Runtime.getRuntime().maxMemory() / 4, MAX_TILE_CACHE_BYTES.toLong()).toInt()
    }
}
