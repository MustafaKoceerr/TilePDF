package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import android.util.LruCache

/**
 * LRU cache of bitmaps bounded by total byte size rather than entry count.
 *
 * Evicted bitmaps are intentionally not recycled: Compose may still be drawing
 * them, so they are left to the garbage collector.
 */
class BitmapCache<K : Any>(maxBytes: Int) : LruCache<K, Bitmap>(maxBytes) {
    override fun sizeOf(key: K, value: Bitmap): Int = value.allocationByteCount
}
