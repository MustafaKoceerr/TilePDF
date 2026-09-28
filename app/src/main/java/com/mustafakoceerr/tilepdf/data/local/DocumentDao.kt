package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DocumentDao {

    @Query("SELECT * FROM documents ORDER BY lastOpenedAt DESC")
    abstract fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE uri = :uri")
    abstract suspend fun get(uri: String): DocumentEntity?

    /** Adds the document on first open; later opens keep the saved position. */
    @Transaction
    open suspend fun recordOpened(uri: String, displayName: String, pageCount: Int, now: Long) {
        insertIfAbsent(
            DocumentEntity(uri, displayName, pageCount, addedAt = now, lastOpenedAt = now)
        )
        markOpened(uri, displayName, pageCount, now)
    }

    @Query(
        "UPDATE documents SET lastPage = :page, pageOffsetFraction = :offsetFraction, " +
            "zoom = :zoom WHERE uri = :uri"
    )
    abstract suspend fun updatePosition(uri: String, page: Int, offsetFraction: Float, zoom: Float)

    @Query("DELETE FROM documents WHERE uri = :uri")
    abstract suspend fun delete(uri: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertIfAbsent(entity: DocumentEntity)

    @Query(
        "UPDATE documents SET displayName = :displayName, pageCount = :pageCount, " +
            "lastOpenedAt = :now WHERE uri = :uri"
    )
    protected abstract suspend fun markOpened(uri: String, displayName: String, pageCount: Int, now: Long)
}
