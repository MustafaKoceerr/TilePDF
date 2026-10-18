package com.mustafakoceerr.tilepdf.feature.reader

import android.view.KeyEvent
import com.mustafakoceerr.tilepdf.feature.reader.components.spreadOf
import com.mustafakoceerr.tilepdf.feature.reader.components.spreads as bookSpreads

/**
 * The page a volume key turns to from [current] of [count], or null for any
 * other key. With [spreads] shown it turns a whole spread, to its left page.
 * At either end it stays put: the key is still taken, so it doesn't
 * suddenly change the volume instead.
 */
internal fun pageForVolumeKey(keyCode: Int, current: Int, count: Int, spreads: Boolean = false): Int? {
    val step = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_DOWN -> 1
        KeyEvent.KEYCODE_VOLUME_UP -> -1
        else -> return null
    }
    if (!spreads) return (current + step).coerceIn(0, count - 1)
    val groups = bookSpreads(count)
    return groups[(spreadOf(current, groups) + step).coerceIn(0, groups.lastIndex)].first()
}
