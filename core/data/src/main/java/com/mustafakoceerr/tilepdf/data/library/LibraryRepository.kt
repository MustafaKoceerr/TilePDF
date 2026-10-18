package com.mustafakoceerr.tilepdf.data.library

import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import kotlinx.coroutines.flow.Flow

/** Documents the user has opened, and where they stopped reading. Device-only. */
interface LibraryRepository {

    /** Most recently opened first. */
    fun observeDocuments(): Flow<List<LibraryDocument>>

    /** The name the library shows for [uri], if it is in the library. */
    suspend fun displayName(uri: String): String?

    suspend fun recordOpened(uri: String, displayName: String, pageCount: Int)

    suspend fun readingPosition(uri: String): ReadingPosition?

    suspend fun saveReadingPosition(uri: String, position: ReadingPosition)

    suspend fun remove(uri: String)
}
