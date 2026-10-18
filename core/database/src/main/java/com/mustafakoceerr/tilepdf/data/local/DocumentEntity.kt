package com.mustafakoceerr.tilepdf.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mustafakoceerr.tilepdf.model.DocumentView
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val uri: String,
    val displayName: String,
    val pageCount: Int,
    val lastPage: Int = 0,
    val pageOffsetFraction: Float = 0f,
    val zoom: Float = 1f,
    /** Added in schema 2; existing rows start unpanned. */
    @ColumnInfo(defaultValue = "0") val panFraction: Float = 0f,
    val addedAt: Long,
    val lastOpenedAt: Long,
    /** Added in schema 5. */
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    /** Added in schema 6: pages turned clockwise, in degrees. */
    @ColumnInfo(defaultValue = "0") val rotation: Int = 0,
    /** Added in schema 6. */
    @ColumnInfo(defaultValue = "0") val cropMargins: Boolean = false,
)

fun DocumentEntity.asExternalModel() = LibraryDocument(
    uri = uri,
    displayName = displayName,
    pageCount = pageCount,
    position = ReadingPosition(lastPage, pageOffsetFraction, zoom, panFraction),
    lastOpenedAt = lastOpenedAt,
    addedAt = addedAt,
    favorite = favorite,
)

fun DocumentEntity.view() = DocumentView(rotation = rotation, cropMargins = cropMargins)
