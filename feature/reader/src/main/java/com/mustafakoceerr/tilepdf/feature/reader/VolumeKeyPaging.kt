package com.mustafakoceerr.tilepdf.feature.reader

import android.view.KeyEvent

/**
 * The page a volume key turns to from [current] of [count], or null for any
 * other key. At either end it stays put: the key is still taken, so it
 * doesn't suddenly change the volume instead.
 */
internal fun pageForVolumeKey(keyCode: Int, current: Int, count: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_VOLUME_DOWN -> (current + 1).coerceAtMost(count - 1)
    KeyEvent.KEYCODE_VOLUME_UP -> (current - 1).coerceAtLeast(0)
    else -> null
}
