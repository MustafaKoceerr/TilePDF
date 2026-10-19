package com.mustafakoceerr.tilepdf.feature.reader

import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

/** How long the way back stays on offer after a jump. */
internal const val JUMP_BACK_TIMEOUT_MS = 8_000L

/**
 * Forgets the way back once the reader has stayed at a jump's target for
 * [timeoutMs]: the "Back to page" chip then fades out instead of staying for good.
 */
internal class JumpBackTimer(private val scope: CoroutineScope, private val timeoutMs: Long = JUMP_BACK_TIMEOUT_MS) {

    private var job: Job? = null

    /** Starts the time over, as each jump does. */
    fun restart(onTimeout: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(timeoutMs)
            onTimeout()
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
