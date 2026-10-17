package com.mustafakoceerr.tilepdf.data.annotation

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Writes one document's annotation draft into its file. A save that fails
 * for a known reason is recorded in the draft and counts as done; only an
 * unexpected I/O error is retried.
 */
@HiltWorker
class SaveAnnotationsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val saver: AnnotationSaving,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uri = inputData.getString(KEY_URI) ?: return Result.failure()
        return try {
            saver.save(uri)
            Result.success()
        } catch (_: IOException) {
            if (runAttemptCount + 1 < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_URI = "uri"
        private const val MAX_ATTEMPTS = 3
    }
}
