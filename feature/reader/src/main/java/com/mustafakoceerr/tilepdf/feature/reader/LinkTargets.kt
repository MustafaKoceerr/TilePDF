package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.feature.reader.components.PageJump

/**
 * Where a link sends the reader. A web or mail link leaves the app, so it is
 * shown first and opened only once confirmed, as in other readers: a PDF can
 * dress any address up as harmless text.
 */
internal data class LinkTargets(
    val jump: PageJump? = null,
    val linkToConfirm: String? = null,
    val externalLink: String? = null,
) {
    fun tapped(uri: String): LinkTargets = copy(linkToConfirm = uri)

    fun confirmed(): LinkTargets = copy(linkToConfirm = null, externalLink = linkToConfirm ?: externalLink)

    fun dismissed(): LinkTargets = copy(linkToConfirm = null)
}
