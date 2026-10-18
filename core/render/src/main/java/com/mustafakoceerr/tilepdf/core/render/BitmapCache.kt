package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import android.util.LruCache

/**
 * LRU cache of bitmaps bounded by total byte size rather than entry count.
 *
 * Evicted bitmaps are never recycled here, since Compose may still be drawing
 * them. [onEvicted] lets the owner decide when a bitmap is safe to reuse.
 */
class BitmapCache<K : Any>(
    maxBytes: Int,
    private val onEvicted: (key: K, bitmap: Bitmap) -> Unit = { _, _ -> },
) : LruCache<K, Bitmap>(maxBytes) {

    override fun sizeOf(key: K, value: Bitmap): Int = value.allocationByteCount

    override fun entryRemoved(evicted: Boolean, key: K, oldValue: Bitmap, newValue: Bitmap?) {
        if (evicted) onEvicted(key, oldValue)
    }
}
