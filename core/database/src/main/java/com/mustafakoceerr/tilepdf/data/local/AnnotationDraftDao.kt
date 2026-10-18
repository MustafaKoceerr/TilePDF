package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AnnotationDraftDao {

    @Query("SELECT * FROM annotation_drafts WHERE uri = :uri")
    abstract fun observe(uri: String): Flow<AnnotationDraftEntity?>

    @Query("SELECT * FROM annotation_drafts WHERE uri = :uri")
    abstract suspend fun get(uri: String): AnnotationDraftEntity?

    @Upsert
    abstract suspend fun upsert(draft: AnnotationDraftEntity)

    @Query("DELETE FROM annotation_drafts WHERE uri = :uri")
    abstract suspend fun delete(uri: String)

    @Query("SELECT uri FROM annotation_drafts WHERE dirty")
    abstract suspend fun dirtyUris(): List<String>

    /**
     * Reads, transforms and stores the draft of [uri] in one transaction, so
     * concurrent updates can't overwrite each other. Returns what was stored;
     * nothing is written when there is no draft or [transform] returns null.
     */
    @Transaction
    open suspend fun update(uri: String, transform: (AnnotationDraftEntity) -> AnnotationDraftEntity?): AnnotationDraftEntity? {
        val updated = get(uri)?.let(transform) ?: return null
        upsert(updated)
        return updated
    }
}
