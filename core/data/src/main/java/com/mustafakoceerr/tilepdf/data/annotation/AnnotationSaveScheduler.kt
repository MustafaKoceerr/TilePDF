package com.mustafakoceerr.tilepdf.data.annotation

/** Saves a document's annotation draft in the background, even if the app is closed. */
fun interface AnnotationSaveScheduler {
    fun requestSave(uri: String)
}
