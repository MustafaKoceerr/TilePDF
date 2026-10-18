package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE uri = :uri ORDER BY pageIndex")
    fun observe(uri: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE uri = :uri AND pageIndex = :pageIndex")
    suspend fun delete(uri: String, pageIndex: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE uri = :uri AND pageIndex = :pageIndex)")
    suspend fun exists(uri: String, pageIndex: Int): Boolean
}
