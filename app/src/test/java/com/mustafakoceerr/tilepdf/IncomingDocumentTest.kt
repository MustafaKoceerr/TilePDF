package com.mustafakoceerr.tilepdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IncomingDocumentTest {

    private val pdf = "content://downloads/a.pdf"

    @Test
    fun viewOfContentUriOpensIt() {
        assertEquals(pdf, incomingDocumentUri(ACTION_VIEW, data = pdf, stream = null, type = "application/pdf"))
    }

    @Test
    fun viewOfFileUriIsIgnored() {
        assertNull(incomingDocumentUri(ACTION_VIEW, data = "file:///sdcard/a.pdf", stream = null, type = "application/pdf"))
    }

    @Test
    fun sharedPdfStreamOpensIt() {
        assertEquals(pdf, incomingDocumentUri(ACTION_SEND, data = null, stream = pdf, type = "application/pdf"))
    }

    @Test
    fun sharedXPdfStreamOpensIt() {
        // Some apps label PDFs with the legacy type.
        assertEquals(pdf, incomingDocumentUri(ACTION_SEND, data = null, stream = pdf, type = "application/x-pdf"))
    }

    @Test
    fun sharedOctetStreamIsIgnored() {
        // A share must say what it is; "Open with" is where unknown types are matched by name.
        assertNull(incomingDocumentUri(ACTION_SEND, data = null, stream = pdf, type = "application/octet-stream"))
    }

    @Test
    fun sharedTextIsIgnored() {
        assertNull(incomingDocumentUri(ACTION_SEND, data = null, stream = pdf, type = "text/plain"))
    }

    @Test
    fun launcherStartOpensNothing() {
        assertNull(incomingDocumentUri("android.intent.action.MAIN", data = null, stream = null, type = null))
    }

    private companion object {
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val ACTION_SEND = "android.intent.action.SEND"
    }
}
