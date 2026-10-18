package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlin.math.abs

/** Pages the reader may scroll from a jump's target before going back no longer makes sense. */
private const val MAX_DRIFT_PAGES = 3

/**
 * Where the reader was before the last jump (contents, bookmark, link…), so
 * "Sayfa 12'ye dön" can take them back. One level only, as in most readers.
 */
internal data class JumpHistory(val origin: ReadingPosition? = null, val target: Int? = null) {

    /** A jump from [from] to page [to]; staying on the same page isn't one. */
    fun jumped(from: ReadingPosition, to: Int): JumpHistory =
        if (from.pageIndex == to) this else JumpHistory(from, to)

    /** Forgets the origin once the reader has scrolled well away from the target. */
    fun scrolledTo(page: Int): JumpHistory =
        if (target != null && abs(page - target) > MAX_DRIFT_PAGES) JumpHistory() else this
}
