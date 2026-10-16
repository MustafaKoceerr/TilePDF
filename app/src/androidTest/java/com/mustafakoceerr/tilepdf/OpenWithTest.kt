package com.mustafakoceerr.tilepdf

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.mustafakoceerr.tilepdf.feature.library.R as LibraryR
import com.mustafakoceerr.tilepdf.feature.reader.R as ReaderR

/**
 * Another app opens a PDF with TilePDF ("Open with" / "Share"). PDFs are
 * published to MediaStore Downloads, a real provider that grants no
 * persistable access, so the app has to keep its own copy.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val published = mutableListOf<Uri>()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @After
    fun tearDown() {
        // Finish first: after onNewIntent the scenario can't drive the activity
        // to DESTROYED by itself.
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            listOf(Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.STARTED, Stage.CREATED)
                .flatMap { monitor.getActivitiesInStage(it) }
                .forEach { it.finish() }
        }
        if (::scenario.isInitialized) scenario.close()
        // finish() is asynchronous: wait until every activity is gone, or the
        // next test's intent can land on one that is still finishing.
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline && aliveActivities() > 0) Thread.sleep(50)
        published.forEach { context.contentResolver.delete(it, null, null) }
    }

    private fun aliveActivities(): Int {
        var count = 0
        instrumentation.runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            count = listOf(Stage.PRE_ON_CREATE, Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED, Stage.RESTARTED)
                .sumOf { monitor.getActivitiesInStage(it).size }
        }
        return count
    }

    /** Display names MediaStore actually gave the published files. */
    private val names = mutableMapOf<Uri, String>()

    /** Publishes [asset] under a name unique to this run; MediaStore renames duplicates. */
    private fun publish(name: String, asset: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "$name-${System.nanoTime()}.pdf")
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
        context.contentResolver.openOutputStream(uri)!!.use { output ->
            instrumentation.context.assets.open(asset).use { it.copyTo(output) }
        }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        names[uri] = context.contentResolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)!!
            .use { it.moveToFirst(); it.getString(0) }
        return uri.also { published += it }
    }

    private fun nameOf(uri: Uri) = names.getValue(uri)

    private fun viewIntent(uri: Uri) = Intent(Intent.ACTION_VIEW)
        .setClassName(context, MainActivity::class.java.name)
        .setDataAndType(uri, "application/pdf")

    private fun openWith(uri: Uri) {
        scenario = ActivityScenario.launch(viewIntent(uri))
    }

    private fun string(id: Int) = context.getString(id)

    private fun await(text: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    /** The reader shows [title]: its search button exists nowhere else. */
    private fun awaitReader(title: String) = compose.waitUntil(10_000) {
        compose.onAllNodesWithContentDescription(string(ReaderR.string.reader_search)).fetchSemanticsNodes().isNotEmpty() &&
            compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun opensTheSharedDocument() {
        val shared = publish("open-with-test", "sample.pdf")
        openWith(shared)
        awaitReader(nameOf(shared))
    }

    @Test
    fun opensASharedStream() {
        val shared = publish("open-with-test", "sample.pdf")
        val intent = Intent(Intent.ACTION_SEND)
            .setClassName(context, MainActivity::class.java.name)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, shared)
        scenario = ActivityScenario.launch(intent)
        awaitReader(nameOf(shared))
    }

    @Test
    fun rotationKeepsOneReader() {
        val shared = publish("open-with-test", "sample.pdf")
        openWith(shared)
        awaitReader(nameOf(shared))
        scenario.recreate()
        awaitReader(nameOf(shared))

        // One reader on the stack: back lands on the library, not another reader.
        pressBack()
        await(string(LibraryR.string.library_title))
    }

    @Test
    fun anotherDocumentReplacesTheOpenReader() {
        // Not ActivityScenario: after onNewIntent it loses track of the activity.
        val first = publish("open-with-test", "sample.pdf")
        context.startActivity(viewIntent(first).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        awaitReader(nameOf(first))

        // A second "Open with" while the reader is showing arrives in onNewIntent.
        val second = publish("open-with-second", "outline.pdf")
        context.startActivity(viewIntent(second).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        awaitReader(nameOf(second))
    }

    @Test
    fun wrongThenCorrectPasswordOpens() {
        val locked = publish("open-with-locked", "encrypted.pdf")
        openWith(locked)
        await(string(ReaderR.string.reader_password_title))

        compose.onNode(hasSetTextAction()).performTextInput("wrong")
        compose.onNodeWithText(string(ReaderR.string.reader_password_open)).performClick()
        await(string(ReaderR.string.reader_password_wrong))

        compose.onNode(hasSetTextAction()).performTextReplacement("secret")
        compose.onNodeWithText(string(ReaderR.string.reader_password_open)).performClick()
        awaitReader(nameOf(locked))
    }

    @Test
    fun cancellingThePasswordLeavesTheReader() {
        openWith(publish("open-with-locked", "encrypted.pdf"))
        await(string(ReaderR.string.reader_password_title))
        compose.onNodeWithText(string(ReaderR.string.cancel)).performClick()
        await(string(LibraryR.string.library_title))
    }
}
