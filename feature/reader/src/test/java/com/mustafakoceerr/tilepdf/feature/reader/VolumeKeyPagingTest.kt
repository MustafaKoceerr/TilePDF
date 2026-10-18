package com.mustafakoceerr.tilepdf.feature.reader

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VolumeKeyPagingTest {

    @Test
    fun volumeDownTurnsForward() = assertEquals(5, pageForVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN, current = 4, count = 10))

    @Test
    fun volumeUpTurnsBack() = assertEquals(3, pageForVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, current = 4, count = 10))

    @Test
    fun lastPageStays() = assertEquals(9, pageForVolumeKey(KeyEvent.KEYCODE_VOLUME_DOWN, current = 9, count = 10))

    @Test
    fun firstPageStays() = assertEquals(0, pageForVolumeKey(KeyEvent.KEYCODE_VOLUME_UP, current = 0, count = 10))

    @Test
    fun otherKeysAreNotPaging() = assertNull(pageForVolumeKey(KeyEvent.KEYCODE_VOLUME_MUTE, current = 4, count = 10))
}
