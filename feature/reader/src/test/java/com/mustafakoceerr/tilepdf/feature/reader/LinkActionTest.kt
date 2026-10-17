package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PdfLink
import com.mustafakoceerr.tilepdf.feature.reader.components.PageJump
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkActionTest {

    @Test
    fun webAndMailLinksOpen() {
        for (uri in listOf("https://example.com", "http://example.com/a?b=c", "HTTPS://EXAMPLE.COM", "mailto:a@b.c")) {
            assertEquals(uri, LinkAction.Open(uri), linkAction(PdfLink.Uri(uri), pageCount = 3))
        }
    }

    @Test
    fun otherSchemesAreIgnored() {
        for (uri in listOf("javascript:alert(1)", "JavaScript:x", "intent://x#Intent;end", "file:///sdcard/a", "content://x/y", "", "example.com")) {
            assertEquals(uri, LinkAction.Ignore, linkAction(PdfLink.Uri(uri), pageCount = 3))
        }
    }

    @Test
    fun goToInRangeJumps() {
        assertEquals(LinkAction.Jump(PageJump(2, 200f)), linkAction(PdfLink.GoTo(2, 200f), pageCount = 3))
        assertEquals(LinkAction.Jump(PageJump(0, null)), linkAction(PdfLink.GoTo(0, null), pageCount = 3))
    }

    @Test
    fun goToOutOfRangeIsIgnored() {
        assertEquals(LinkAction.Ignore, linkAction(PdfLink.GoTo(3, null), pageCount = 3))
        assertEquals(LinkAction.Ignore, linkAction(PdfLink.GoTo(-1, null), pageCount = 3))
    }

    @Test
    fun nullIsNoLink() {
        // Not a link at all: the tap toggles full screen instead.
        assertEquals(LinkAction.NoLink, linkAction(null, pageCount = 3))
    }
}
