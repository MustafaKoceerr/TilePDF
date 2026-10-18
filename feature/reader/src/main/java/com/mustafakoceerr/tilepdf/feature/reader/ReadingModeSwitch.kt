package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.model.ReadingPosition

/**
 * Where the other reading mode starts: the page that fills most of the
 * screen, from its top. Once the top page is more than half scrolled away,
 * the next page is the one being read. Zoom doesn't carry over.
 */
internal fun positionForModeSwitch(position: ReadingPosition, pageCount: Int): ReadingPosition {
    val page = if (position.pageOffsetFraction > 0.5f) position.pageIndex + 1 else position.pageIndex
    return ReadingPosition(pageIndex = page.coerceAtMost(pageCount - 1))
}
