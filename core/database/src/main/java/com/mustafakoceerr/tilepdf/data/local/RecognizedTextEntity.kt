package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Entity
import androidx.room.ForeignKey

/**
 * Words recognised on page [pageIndex] of [uri] that aren't in the file yet,
 * as JSON. One row per page keeps rows small: a draft row holding a whole
 * scanned book would outgrow SQLite's cursor window. Removed with its
 * library entry.
 */
@Entity(
    tableName = "recognized_text",
    primaryKeys = ["uri", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["uri"],
            childColumns = ["uri"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
)
data class RecognizedTextEntity(
    val uri: String,
    val pageIndex: Int,
    val words: String,
)
