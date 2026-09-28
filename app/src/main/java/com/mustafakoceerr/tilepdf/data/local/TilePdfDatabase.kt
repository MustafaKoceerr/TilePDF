package com.mustafakoceerr.tilepdf.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DocumentEntity::class],
    version = 2,
    autoMigrations = [
        // 1 -> 2: DocumentEntity.panFraction
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class TilePdfDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
