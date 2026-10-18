package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/** A bookmarked page; removed with its library entry. */
@Entity(
    tableName = "bookmarks",
    primaryKeys = ["uri", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["uri"],
            childColumns = ["uri"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("uri")],
)
data class BookmarkEntity(
    val uri: String,
    val pageIndex: Int,
    val createdAt: Long,
)
