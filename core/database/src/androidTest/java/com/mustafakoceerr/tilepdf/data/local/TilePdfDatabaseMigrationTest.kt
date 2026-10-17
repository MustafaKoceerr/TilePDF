package com.mustafakoceerr.tilepdf.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val DB_NAME = "migration-test.db"

@RunWith(AndroidJUnit4::class)
class TilePdfDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TilePdfDatabase::class.java,
    )

    @Test
    fun migrate1To2KeepsLibraryAndDefaultsPan() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL(
                "INSERT INTO documents (uri, displayName, pageCount, lastPage, " +
                    "pageOffsetFraction, zoom, addedAt, lastOpenedAt) " +
                    "VALUES ('content://book', 'Book.pdf', 300, 41, 0.5, 2.0, 1, 2)"
            )
        }

        // Validates the migrated schema against the exported 2.json.
        helper.runMigrationsAndValidate(DB_NAME, 2, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TilePdfDatabase::class.java, DB_NAME).build()
        val document = runBlocking { database.documentDao().get("content://book") }!!
        database.close()

        assertEquals(41, document.lastPage)
        assertEquals(2f, document.zoom)
        assertEquals(0f, document.panFraction)
    }

    @Test
    fun migrate2To3KeepsLibraryAndAddsBookmarks() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL(
                "INSERT INTO documents (uri, displayName, pageCount, lastPage, " +
                    "pageOffsetFraction, zoom, panFraction, addedAt, lastOpenedAt) " +
                    "VALUES ('content://book', 'Book.pdf', 300, 41, 0.5, 2.0, 0.25, 1, 2)"
            )
        }

        // Validates the migrated schema against the exported 3.json.
        helper.runMigrationsAndValidate(DB_NAME, 3, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TilePdfDatabase::class.java, DB_NAME).build()
        val document = runBlocking {
            database.bookmarkDao().insert(BookmarkEntity("content://book", pageIndex = 7, createdAt = 3))
            database.documentDao().get("content://book")
        }!!
        val bookmarks = runBlocking { database.bookmarkDao().observe("content://book").first() }
        database.close()

        assertEquals(41, document.lastPage)
        assertEquals(0.25f, document.panFraction)
        assertEquals(listOf(7), bookmarks.map { it.pageIndex })
    }
}
