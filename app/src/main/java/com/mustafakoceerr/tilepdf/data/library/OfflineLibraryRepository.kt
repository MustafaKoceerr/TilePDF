package com.mustafakoceerr.tilepdf.data.library

import com.mustafakoceerr.tilepdf.data.local.DocumentDao
import com.mustafakoceerr.tilepdf.data.local.asExternalModel
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class OfflineLibraryRepository @Inject constructor(
    private val dao: DocumentDao,
) : LibraryRepository {

    override fun observeDocuments(): Flow<List<LibraryDocument>> =
        dao.observeAll().map { entities -> entities.map { it.asExternalModel() } }

    override suspend fun recordOpened(uri: String, displayName: String, pageCount: Int) =
        dao.recordOpened(uri, displayName, pageCount, now = System.currentTimeMillis())

    override suspend fun readingPosition(uri: String): ReadingPosition? =
        dao.get(uri)?.asExternalModel()?.position

    override suspend fun saveReadingPosition(uri: String, position: ReadingPosition) =
        dao.updatePosition(
            uri,
            position.pageIndex,
            position.pageOffsetFraction,
            position.zoom,
            position.panFraction,
        )

    override suspend fun remove(uri: String) = dao.delete(uri)
}
