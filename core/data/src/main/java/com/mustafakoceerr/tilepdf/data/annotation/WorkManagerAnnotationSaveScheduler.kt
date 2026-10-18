package com.mustafakoceerr.tilepdf.data.annotation

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WorkManagerAnnotationSaveScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AnnotationSaveScheduler {

    /**
     * One save at a time per document: a request made while one runs is
     * queued after it, so edits made meanwhile are saved too.
     */
    override fun requestSave(uri: String) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "save-annotations:$uri",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SaveAnnotationsWorker>()
                .setInputData(workDataOf(SaveAnnotationsWorker.KEY_URI to uri))
                .build(),
        )
    }
}
