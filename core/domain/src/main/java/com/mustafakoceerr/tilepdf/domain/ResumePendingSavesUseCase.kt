package com.mustafakoceerr.tilepdf.domain

import com.mustafakoceerr.tilepdf.data.annotation.AnnotationRepository
import com.mustafakoceerr.tilepdf.data.annotation.AnnotationSaveScheduler
import javax.inject.Inject

/** Saves drafts a previous run left unsaved, e.g. when the app was killed first. */
class ResumePendingSavesUseCase @Inject constructor(
    private val repository: AnnotationRepository,
    private val scheduler: AnnotationSaveScheduler,
) {
    suspend operator fun invoke() {
        repository.dirtyUris().forEach(scheduler::requestSave)
    }
}
