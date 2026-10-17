package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.data.local.AnnotationDraftDao
import com.mustafakoceerr.tilepdf.data.local.AnnotationDraftEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeAnnotationDraftDao : AnnotationDraftDao() {

    val rows = MutableStateFlow<Map<String, AnnotationDraftEntity>>(emptyMap())

    override fun observe(uri: String): Flow<AnnotationDraftEntity?> = rows.map { it[uri] }

    override suspend fun get(uri: String): AnnotationDraftEntity? = rows.value[uri]

    override suspend fun upsert(draft: AnnotationDraftEntity) {
        rows.value += draft.uri to draft
    }

    override suspend fun delete(uri: String) {
        rows.value -= uri
    }

    override suspend fun dirtyUris(): List<String> = rows.value.values.filter { it.dirty }.map { it.uri }
}
