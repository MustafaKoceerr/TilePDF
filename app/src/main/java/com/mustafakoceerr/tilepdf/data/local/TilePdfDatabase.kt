package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DocumentEntity::class], version = 1)
abstract class TilePdfDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
