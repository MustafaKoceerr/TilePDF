package com.mustafakoceerr.tilepdf.core.common

import android.view.KeyEvent

/**
 * An activity that lets a screen see hardware keys before the system does,
 * e.g. volume keys that would otherwise change the volume.
 */
interface KeyEventHost {

    /** [handler] returns true for a key it took; null stops listening. */
    fun setKeyHandler(handler: ((KeyEvent) -> Boolean)?)
}
