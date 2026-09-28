package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import androidx.compose.runtime.Stable

/**
 * Supplies rendered page images to the UI. The screen only asks for "page N at
 * width W"; whether that comes from a whole-page draft or tiles is decided
 * behind this interface.
 */
@Stable
interface PageImageSource {

    /** An image already in memory, returned without suspending. */
    fun cached(pageIndex: Int, widthPx: Int): Bitmap?

    suspend fun load(pageIndex: Int, widthPx: Int): Bitmap
}
