package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecognizedTextDao {

    /** The pages of [uri] with words waiting to be saved. */
    @Query("SELECT pageIndex FROM recognized_text WHERE uri = :uri ORDER BY pageIndex")
    fun observePages(uri: String): Flow<List<Int>>

    @Query("SELECT * FROM recognized_text WHERE uri = :uri AND pageIndex IN (:pages)")
    suspend fun get(uri: String, pages: Collection<Int>): List<RecognizedTextEntity>

    @Upsert
    suspend fun upsert(page: RecognizedTextEntity)

    @Query("DELETE FROM recognized_text WHERE uri = :uri AND pageIndex IN (:pages)")
    suspend fun delete(uri: String, pages: Collection<Int>)

    @Query("UPDATE recognized_text SET uri = :to WHERE uri = :from")
    suspend fun move(from: String, to: String)
}
