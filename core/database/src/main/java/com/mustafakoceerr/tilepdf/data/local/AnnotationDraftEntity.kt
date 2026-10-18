package com.mustafakoceerr.tilepdf.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * A document's annotation draft; removed with its library entry. The
 * annotation, foreign-ref and form-value columns are JSON written by core:data.
 */
@Entity(
    tableName = "annotation_drafts",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["uri"],
            childColumns = ["uri"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AnnotationDraftEntity(
    @PrimaryKey val uri: String,
    val annotations: String,
    val removedForeign: String,
    val dirty: Boolean,
    /** Stamp of the file the draft is based on. */
    val baseSize: Long,
    val baseModified: Long?,
    /** `SaveError` name of the last failed save, if any. */
    val saveError: String?,
    val updatedAt: Long,
    /** Added in schema 7: pending form values, a JSON object of field name to value. */
    @ColumnInfo(defaultValue = "{}") val formValues: String = "{}",
)
