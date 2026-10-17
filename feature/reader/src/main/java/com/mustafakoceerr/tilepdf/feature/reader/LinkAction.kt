package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.core.pdf.PdfLink
import com.mustafakoceerr.tilepdf.feature.reader.components.PageJump

/** What tapping a link does. */
internal sealed interface LinkAction {
    data class Jump(val jump: PageJump) : LinkAction

    /** Hand [uri] to another app (browser, mail). */
    data class Open(val uri: String) : LinkAction

    /** A link the reader won't follow; the tap does nothing. */
    data object Ignore : LinkAction

    /** No link there: the tap belongs to the page itself. */
    data object NoLink : LinkAction
}

/**
 * Only web and mail links leave the app: a PDF can carry `javascript:`,
 * `intent:` or `file:` links, which must never be followed.
 */
internal fun linkAction(link: PdfLink?, pageCount: Int): LinkAction = when (link) {
    is PdfLink.GoTo ->
        if (link.pageIndex in 0 until pageCount) LinkAction.Jump(PageJump(link.pageIndex, link.topPt)) else LinkAction.Ignore
    is PdfLink.Uri ->
        if (link.uri.substringBefore(':', "").lowercase() in OPENABLE_SCHEMES) LinkAction.Open(link.uri) else LinkAction.Ignore
    null -> LinkAction.NoLink
}

private val OPENABLE_SCHEMES = setOf("http", "https", "mailto")
