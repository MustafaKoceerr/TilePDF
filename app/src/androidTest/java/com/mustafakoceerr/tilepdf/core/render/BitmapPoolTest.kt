package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BitmapPoolTest {

    private var now = 0L
    private val pool = BitmapPool(maxBytes = 2 * TILE_BYTES, quarantineMs = 100, clock = { now })

    @Test
    fun releasedBitmapIsQuarantinedBeforeReuse() {
        val bitmap = tileBitmap()
        pool.release(bitmap)

        now = 99
        assertNull(pool.acquire(TILE_BYTES))
        now = 100
        assertSame(bitmap, pool.acquire(TILE_BYTES))
        assertEquals(0, pool.size)
    }

    @Test
    fun onlyLargeEnoughBitmapsAreReused() {
        val small = createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        pool.release(small)
        now = 1_000

        assertNull(pool.acquire(TILE_BYTES))
        assertSame(small, pool.acquire(100 * 100 * 4))
    }

    @Test
    fun fullPoolDropsExtraBitmaps() {
        repeat(3) { pool.release(tileBitmap()) }
        assertEquals(2, pool.size)
    }

    private fun tileBitmap() = createBitmap(TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)

    private companion object {
        const val TILE_BYTES = TILE_SIZE * TILE_SIZE * 4
    }
}
