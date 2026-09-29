package com.mustafakoceerr.tilepdf.core.render

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import com.mustafakoceerr.tilepdf.testing.TestAssets

/** The source must be driven from the main thread, as the UI does. */
@RunWith(AndroidJUnit4::class)
class TiledPageImageSourceTest {

    private val renderer = PageRenderer(Dispatchers.IO)
    /**
     * Renders resume only when the test yields, so assertions right after a
     * request see its effect before any render completes.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** What the app uses; renders may complete without ever suspending. */
    private val immediateScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var document: PdfDocument

    @Before
    fun setUp() = runBlocking {
        document = TestAssets.openDocument("sample.pdf")
    }

    @After
    fun tearDown() {
        scope.cancel()
        immediateScope.cancel()
        document.close()
    }

    @Test
    fun previousLevelStaysAsFallbackUntilNewLevelCoversPage() = onMain {
        val source = TiledPageImageSource(document, renderer, scope)
        source.requestTiles(allTiles(page = 0, renderWidth = 1000))
        source.awaitRenders()
        assertEquals(setOf(1000), source.renderedWidths(0))

        source.requestTiles(allTiles(page = 0, renderWidth = 2000))
        // New level still rendering: the old one keeps the page sharp.
        assertEquals(setOf(1000), source.renderedWidths(0))

        source.awaitRenders()
        assertEquals(setOf(2000), source.renderedWidths(0))
    }

    @Test
    fun pageLeavingScreenDropsItsFallback() = onMain {
        val source = TiledPageImageSource(document, renderer, scope)
        source.requestTiles(allTiles(page = 0, renderWidth = 1000))
        source.awaitRenders()

        source.requestTiles(allTiles(page = 1, renderWidth = 1000))
        assertTrue(source.renderedTiles(0).isEmpty())
        source.awaitRenders()
    }

    @Test
    fun returningToLevelReusesCachedTilesWithoutRendering() = onMain {
        // Explicit budget: the default depends on the device heap (48 MB on
        // the API 29 emulator, too small for both levels of this page).
        val source = TiledPageImageSource(document, renderer, scope, tileCacheBytes = 64 * TILE_BYTES)
        source.requestTiles(allTiles(page = 0, renderWidth = 1000))
        source.awaitRenders()
        source.requestTiles(allTiles(page = 0, renderWidth = 2000))
        source.awaitRenders()

        source.requestTiles(allTiles(page = 0, renderWidth = 1000))
        // Served from the LRU cache synchronously, no render needed.
        assertEquals(setOf(1000), source.renderedWidths(0))
        assertEquals(allTiles(0, 1000).size, source.renderedTiles(0).size)
    }

    @Test
    fun evictedBitmapsAreReusedOnceOffScreen() = onMain {
        // Room for about one page of tiles, so moving on evicts the previous page.
        val pool = BitmapPool(maxBytes = 64 * TILE_BYTES, quarantineMs = 0)
        val source = TiledPageImageSource(document, renderer, scope, tileCacheBytes = 6 * TILE_BYTES, pool)

        source.requestTiles(allTiles(page = 0, renderWidth = 1000))
        source.awaitRenders()
        val page0Bitmaps = source.bitmaps(0)

        // Page 1 evicts page 0's off-screen tiles into the pool...
        source.requestTiles(allTiles(page = 1, renderWidth = 1000))
        source.awaitRenders()
        // ...and page 2 renders into those same bitmaps instead of allocating.
        source.requestTiles(allTiles(page = 2, renderWidth = 1000))
        source.awaitRenders()

        val reused = source.bitmaps(2).count { bitmap -> page0Bitmaps.any { it === bitmap } }
        assertTrue("expected page 2 to reuse page 0 bitmaps", reused > 0)
    }

    private fun TiledPageImageSource.bitmaps(page: Int) =
        renderedTiles(page).map { it.image.asAndroidBitmap() }

    @Test
    fun rendersThatFinishWithoutSuspendingAreNotLeftPending() = onMain {
        // No cache, so every return to a page renders again. Tiny blank tiles
        // often finish before launch returns; they must still leave pending.
        val source = TiledPageImageSource(document, renderer, immediateScope, tileCacheBytes = 1)
        repeat(10) {
            for (page in 0..1) {
                source.requestTiles(allTiles(page, renderWidth = 300))
                source.awaitRenders()
                assertEquals(0, source.pendingRenderCount)
                assertEquals(allTiles(page, 300).size, source.renderedTiles(page).size)
            }
        }
    }

    private fun allTiles(page: Int, renderWidth: Int): List<Tile> {
        val height = document.pageSizes[page].heightForWidth(renderWidth)
        return TileGrid(page, renderWidth, height)
            .tilesCovering(PixelRect(0, 0, renderWidth, height), prefetch = 0)
    }

    private fun TiledPageImageSource.renderedWidths(page: Int) =
        renderedTiles(page).mapTo(HashSet()) { it.tile.key.renderWidth }

    private fun onMain(block: suspend () -> Unit) = runBlocking {
        withContext(Dispatchers.Main) { block() }
    }

    private companion object {
        const val TILE_BYTES = TILE_SIZE * TILE_SIZE * 4
    }
}
