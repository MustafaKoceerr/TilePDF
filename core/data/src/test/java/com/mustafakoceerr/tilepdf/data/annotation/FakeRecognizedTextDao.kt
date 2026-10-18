package com.mustafakoceerr.tilepdf.data.annotation

import com.mustafakoceerr.tilepdf.data.local.RecognizedTextDao
import com.mustafakoceerr.tilepdf.data.local.RecognizedTextEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeRecognizedTextDao : RecognizedTextDao {

    val rows = MutableStateFlow<Map<Pair<String, Int>, RecognizedTextEntity>>(emptyMap())

    override fun observePages(uri: String): Flow<List<Int>> =
        rows.map { all -> all.keys.filter { it.first == uri }.map { it.second }.sorted() }

    override suspend fun get(uri: String, pages: Collection<Int>): List<RecognizedTextEntity> =
        rows.value.values.filter { it.uri == uri && it.pageIndex in pages }

    override suspend fun upsert(page: RecognizedTextEntity) {
        rows.value += (page.uri to page.pageIndex) to page
    }

    override suspend fun delete(uri: String, pages: Collection<Int>) {
        rows.value = rows.value.filterKeys { (rowUri, page) -> rowUri != uri || page !in pages }
    }

    override suspend fun move(from: String, to: String) {
        rows.value = rows.value.entries.associate { (key, row) ->
            if (key.first == from) (to to key.second) to row.copy(uri = to) else key to row
        }
    }
}
