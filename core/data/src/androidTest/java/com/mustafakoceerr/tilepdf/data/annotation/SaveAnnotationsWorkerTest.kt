package com.mustafakoceerr.tilepdf.data.annotation

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.mustafakoceerr.tilepdf.model.SaveError
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SaveAnnotationsWorkerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val saved = mutableListOf<String>()
    private var failure: Exception? = null

    private val saver = object : AnnotationSaving {
        override suspend fun save(uri: String): SaveResult {
            failure?.let { throw it }
            saved += uri
            return SaveResult.Failed(SaveError.NoWritePermission)
        }

        override suspend fun saveCopy(uri: String, target: String): SaveResult = error("unused")
    }

    private val factory = object : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
            SaveAnnotationsWorker(appContext, workerParameters, saver)
    }

    private fun run(vararg input: Pair<String, String>): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<SaveAnnotationsWorker>(context)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(*input))
            .build()
            .doWork()
    }

    @Test
    fun savesTheGivenUri() {
        // A failure recorded in the draft still finishes the work.
        assertEquals(ListenableWorker.Result.success(), run(SaveAnnotationsWorker.KEY_URI to "content://book"))
        assertEquals(listOf("content://book"), saved)
    }

    @Test
    fun missingUriFails() {
        assertEquals(ListenableWorker.Result.failure(), run())
    }

    @Test
    fun ioErrorIsRetried() {
        failure = IOException("disk")
        assertEquals(ListenableWorker.Result.retry(), run(SaveAnnotationsWorker.KEY_URI to "content://book"))
    }
}
