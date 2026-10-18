package com.mustafakoceerr.tilepdf

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

/**
 * The PDF another app asked us to open ("Open with" or "Share"), or `null`.
 * Only `content://` URIs: other apps can't legitimately hand out `file://`.
 */
internal fun incomingDocumentUri(action: String?, data: String?, stream: String?, type: String?): String? {
    val uri = when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND -> stream.takeIf { type in PDF_MIME_TYPES }
        else -> null
    }
    return uri?.takeIf { it.startsWith("content://") }
}

internal fun Intent.incomingDocumentUri(): String? = incomingDocumentUri(
    action = action,
    data = dataString,
    stream = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.toString(),
    type = type,
)

/** The standard type and the legacy one some apps still send. */
private val PDF_MIME_TYPES = setOf("application/pdf", "application/x-pdf")
