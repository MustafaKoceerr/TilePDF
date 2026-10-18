package com.mustafakoceerr.tilepdf.core.common

import kotlinx.coroutines.CancellationException

/**
 * [runCatching] for suspending code: failures become a [Result], but
 * cancellation is rethrown so a cancelled coroutine actually stops.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
