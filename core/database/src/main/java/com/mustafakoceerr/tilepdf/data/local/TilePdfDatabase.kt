package com.mustafakoceerr.tilepdf.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class, BookmarkEntity::class, AnnotationDraftEntity::class],
    version = 6,
    autoMigrations = [
        // 1 -> 2: DocumentEntity.panFraction
        AutoMigration(from = 1, to = 2),
        // 2 -> 3: bookmarks table
        AutoMigration(from = 2, to = 3),
        // 3 -> 4: annotation drafts table
        AutoMigration(from = 3, to = 4),
        // 4 -> 5: DocumentEntity.favorite
        AutoMigration(from = 4, to = 5),
        // 5 -> 6: DocumentEntity.rotation and cropMargins
        AutoMigration(from = 5, to = 6),
    ],
)
abstract class TilePdfDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao

    abstract fun bookmarkDao(): BookmarkDao

    abstract fun annotationDraftDao(): AnnotationDraftDao
}
