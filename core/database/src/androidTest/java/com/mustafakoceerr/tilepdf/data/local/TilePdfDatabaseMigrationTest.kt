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

    @Test
    fun migrate3To4KeepsLibraryAndAddsDrafts() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL(
                "INSERT INTO documents (uri, displayName, pageCount, lastPage, " +
                    "pageOffsetFraction, zoom, panFraction, addedAt, lastOpenedAt) " +
                    "VALUES ('content://book', 'Book.pdf', 300, 41, 0.5, 2.0, 0.25, 1, 2)"
            )
            db.execSQL("INSERT INTO bookmarks (uri, pageIndex, createdAt) VALUES ('content://book', 7, 3)")
        }

        // Validates the migrated schema against the exported 4.json.
        helper.runMigrationsAndValidate(DB_NAME, 4, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TilePdfDatabase::class.java, DB_NAME).build()
        val draft = AnnotationDraftEntity(
            uri = "content://book", annotations = "[]", removedForeign = "[]", dirty = true,
            baseSize = 10, baseModified = null, saveError = null, updatedAt = 4,
        )
        val (document, bookmarks, stored) = runBlocking {
            database.annotationDraftDao().upsert(draft)
            Triple(
                database.documentDao().get("content://book"),
                database.bookmarkDao().observe("content://book").first(),
                database.annotationDraftDao().get("content://book"),
            )
        }
        database.close()

        assertEquals(41, document!!.lastPage)
        assertEquals(listOf(7), bookmarks.map { it.pageIndex })
        assertEquals(draft, stored)
    }

    @Test
    fun migrate4To5KeepsLibrary() {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL(
                "INSERT INTO documents (uri, displayName, pageCount, lastPage, " +
                    "pageOffsetFraction, zoom, panFraction, addedAt, lastOpenedAt) " +
                    "VALUES ('content://book', 'Book.pdf', 300, 41, 0.5, 2.0, 0.25, 1, 2)"
            )
            db.execSQL(
                "INSERT INTO annotation_drafts (uri, annotations, removedForeign, dirty, baseSize, " +
                    "baseModified, saveError, updatedAt) VALUES ('content://book', '[]', '[]', 1, 10, NULL, NULL, 3)"
            )
        }

        // Validates the migrated schema against the exported 5.json.
        helper.runMigrationsAndValidate(DB_NAME, 5, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TilePdfDatabase::class.java, DB_NAME).build()
        val (document, draft) = runBlocking {
            database.documentDao().get("content://book") to database.annotationDraftDao().get("content://book")
        }
        database.close()

        assertEquals(41, document!!.lastPage)
        assertEquals(false, document.favorite)
        assertEquals(true, draft!!.dirty)
    }

    @Test
    fun migrate5To6KeepsLibraryAndShowsPagesAsBefore() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL(
                "INSERT INTO documents (uri, displayName, pageCount, lastPage, " +
                    "pageOffsetFraction, zoom, panFraction, addedAt, lastOpenedAt, favorite) " +
                    "VALUES ('content://book', 'Book.pdf', 300, 41, 0.5, 2.0, 0.25, 1, 2, 1)"
            )
        }

        // Validates the migrated schema against the exported 6.json.
        helper.runMigrationsAndValidate(DB_NAME, 6, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TilePdfDatabase::class.java, DB_NAME).build()
        val document = runBlocking { database.documentDao().get("content://book") }
        database.close()

        assertEquals(41, document!!.lastPage)
        assertEquals(true, document.favorite)
        assertEquals(0, document.rotation)
        assertEquals(false, document.cropMargins)
    }

    @Test
    fun migrate6To7KeepsDraftsWithNoPendingFormValues() {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL(
                "INSERT INTO documents (uri, displayName, pageCount, lastPage, pageOffsetFraction, zoom, panFraction, " +
                    "addedAt, lastOpenedAt, favorite, rotation, cropMargins) " +
                    "VALUES ('content://book', 'Book.pdf', 3, 1, 0, 1, 0, 1, 2, 0, 90, 1)"
            )
            db.execSQL(
                "INSERT INTO annotation_drafts (uri, annotations, removedForeign, dirty, baseSize, " +
                    "baseModified, saveError, updatedAt) VALUES ('content://book', '[]', '[]', 1, 10, NULL, NULL, 3)"
            )
        }

        // Validates the migrated schema against the exported 7.json.
        helper.runMigrationsAndValidate(DB_NAME, 7, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TilePdfDatabase::class.java, DB_NAME).build()
        val (document, draft) = runBlocking {
            database.documentDao().get("content://book") to database.annotationDraftDao().get("content://book")
        }
        database.close()

        assertEquals(90, document!!.rotation)
        assertEquals(true, draft!!.dirty)
        assertEquals("{}", draft.formValues)
    }

    @Test
    fun migrate7To8AddsRecognizedTextRemovedWithItsDocument() {
        helper.createDatabase(DB_NAME, 7).use { db ->
            db.execSQL(
                "INSERT INTO documents (uri, displayName, pageCount, lastPage, pageOffsetFraction, zoom, panFraction, " +
                    "addedAt, lastOpenedAt, favorite, rotation, cropMargins) " +
                    "VALUES ('content://book', 'Book.pdf', 3, 1, 0, 1, 0, 1, 2, 0, 0, 0)"
            )
        }

        // Validates the migrated schema against the exported 8.json.
        helper.runMigrationsAndValidate(DB_NAME, 8, true).close()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.databaseBuilder(context, TilePdfDatabase::class.java, DB_NAME).build()
        val pages = runBlocking {
            val dao = database.recognizedTextDao()
            dao.upsert(RecognizedTextEntity("content://book", 2, "[]"))
            val before = dao.observePages("content://book").first()
            database.documentDao().delete("content://book")
            before to dao.observePages("content://book").first()
        }
        database.close()

        assertEquals(listOf(2) to emptyList<Int>(), pages)
    }

    @Test
    fun viewSettingsAreStored() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TilePdfDatabase::class.java).build()
        val document = runBlocking {
            database.documentDao().recordOpened("content://book", "Book.pdf", pageCount = 3, now = 1)
            database.documentDao().setRotation("content://book", 270)
            database.documentDao().setCropMargins("content://book", true)
            database.documentDao().get("content://book")
        }
        database.close()
        assertEquals(270, document!!.rotation)
        assertEquals(true, document.cropMargins)
    }

    @Test
    fun setFavoriteIsStored() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, TilePdfDatabase::class.java).build()
        val document = runBlocking {
            database.documentDao().recordOpened("content://book", "Book.pdf", pageCount = 3, now = 1)
            database.documentDao().setFavorite("content://book", true)
            database.documentDao().get("content://book")
        }
        database.close()
        assertEquals(true, document!!.favorite)
    }
}
