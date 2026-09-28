package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import android.os.SystemClock

/**
 * Keeps no-longer-used bitmaps for reuse, so rendering a tile doesn't allocate
 * (and later garbage-collect) a fresh 1 MB bitmap every time.
 *
 * A released bitmap is held back for [quarantineMs] before it's handed out:
 * the RenderThread may still be drawing a frame recorded while the bitmap was
 * on screen, and overwriting it then would flash half-rendered content.
 *
 * Not thread-safe; use from one thread.
 */
class BitmapPool(
    private val maxBytes: Int,
    private val quarantineMs: Long = 100,
    private val clock: () -> Long = SystemClock::uptimeMillis,
) {
    private class Entry(val bitmap: Bitmap, val releasedAt: Long)

    private val entries = ArrayDeque<Entry>()
    private var bytes = 0

    val size: Int get() = entries.size

    /**
     * A bitmap with room for at least [byteCount] bytes, or `null`. The caller
     * must `reconfigure` it to the size it needs.
     */
    fun acquire(byteCount: Int): Bitmap? {
        val now = clock()
        val index = entries.indexOfFirst {
            now - it.releasedAt >= quarantineMs && it.bitmap.allocationByteCount >= byteCount
        }
        if (index < 0) return null
        val bitmap = entries.removeAt(index).bitmap
        bytes -= bitmap.allocationByteCount
        return bitmap
    }

    /** Offers [bitmap] for reuse; dropped if the pool is full. */
    fun release(bitmap: Bitmap) {
        if (bitmap.isRecycled || !bitmap.isMutable) return
        if (bytes + bitmap.allocationByteCount > maxBytes) return
        entries.addLast(Entry(bitmap, clock()))
        bytes += bitmap.allocationByteCount
    }

    fun clear() {
        entries.clear()
        bytes = 0
    }
}
