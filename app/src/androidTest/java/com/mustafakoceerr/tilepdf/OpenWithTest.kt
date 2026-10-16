package com.mustafakoceerr.tilepdf

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.mustafakoceerr.tilepdf.feature.library.R as LibraryR
import com.mustafakoceerr.tilepdf.feature.reader.R as ReaderR

/**
 * Another app opens a PDF with TilePDF ("Open with"). The PDF is published
 * to MediaStore Downloads, a real provider that grants no persistable access,
 * so the app has to keep its own copy.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var shared: Uri
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun publishSample() {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "open-with-test.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        shared = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(shared)!!.use { output ->
            instrumentation.context.assets.open("sample.pdf").use { it.copyTo(output) }
        }
        context.contentResolver.update(shared, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
        context.contentResolver.delete(shared, null, null)
    }

    private fun openWith() {
        val intent = Intent(Intent.ACTION_VIEW)
            .setClassName(context, MainActivity::class.java.name)
            .setDataAndType(shared, "application/pdf")
        scenario = ActivityScenario.launch(intent)
    }

    /** The reader is showing: its search button exists nowhere else. */
    private fun awaitReader() = compose.waitUntil(10_000) {
        compose.onAllNodesWithContentDescription(context.getString(ReaderR.string.reader_search))
            .fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithText("open-with-test.pdf").fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun opensTheSharedDocument() {
        openWith()
        awaitReader()
    }

    @Test
    fun opensASharedStream() {
        val intent = Intent(Intent.ACTION_SEND)
            .setClassName(context, MainActivity::class.java.name)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, shared)
        scenario = ActivityScenario.launch(intent)
        awaitReader()
    }

    @Test
    fun rotationDoesNotReopen() {
        openWith()
        awaitReader()
        scenario.recreate()
        awaitReader()

        // One reader on the stack: back lands on the library, not another reader.
        pressBack()
        val library = context.getString(LibraryR.string.library_title)
        compose.waitUntil(10_000) { compose.onAllNodesWithText(library).fetchSemanticsNodes().isNotEmpty() }
    }
}
