package com.mustafakoceerr.tilepdf.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenNameTest {

    @Test
    fun aRouteIsNamedAfterItsClassWithoutArguments() {
        // The arguments hold the document's URI, which must never be sent.
        assertEquals("reader", screenNameOf("com.mustafakoceerr.tilepdf.feature.reader.navigation.ReaderRoute/{uri}"))
        assertEquals("library", screenNameOf("com.mustafakoceerr.tilepdf.feature.library.navigation.LibraryRoute"))
        assertEquals("settings", screenNameOf("com.mustafakoceerr.tilepdf.feature.settings.navigation.SettingsRoute?tab={tab}"))
    }

    @Test
    fun noRouteHasNoName() {
        assertNull(screenNameOf(null))
    }
}
