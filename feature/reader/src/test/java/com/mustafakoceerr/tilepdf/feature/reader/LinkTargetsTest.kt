package com.mustafakoceerr.tilepdf.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkTargetsTest {

    private val uri = "https://example.com/tilepdf"

    @Test
    fun aTappedWebLinkWaitsForConfirmation() {
        val links = LinkTargets().tapped(uri)

        assertEquals(uri, links.linkToConfirm)
        assertNull(links.externalLink)
    }

    @Test
    fun aConfirmedLinkIsOpened() {
        val links = LinkTargets().tapped(uri).confirmed()

        assertNull(links.linkToConfirm)
        assertEquals(uri, links.externalLink)
    }

    @Test
    fun aDismissedLinkIsNotOpened() {
        val links = LinkTargets().tapped(uri).dismissed()

        assertNull(links.linkToConfirm)
        assertNull(links.externalLink)
    }
}
