package com.mustafakoceerr.tilepdf.data.document

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.pdf.Pdfium
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SafDocumentRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, Dispatchers.IO.limitedParallelism(1))
    private val repository = SafDocumentRepository(context, Dispatchers.IO, pdfium)
    // Not backed up: copies of received PDFs must not leave the device.
    private val copies = File(context.noBackupFilesDir, "documents")
    private val sampleBytes by lazy { context.assets.open("sample.pdf").use { it.readBytes() } }

    @Before
    @After
    fun clean() {
        copies.deleteRecursively()
    }

    @Test
    fun unsizedStreamIsCopied() = runBlocking {
        val prepared = repository.prepare(TestDocumentsProvider.uri("pipe", "sample.pdf"))
        val file = File(prepared.toUri().path!!)
        assertEquals("file", prepared.toUri().scheme)
        assertEquals(copies.canonicalPath, file.parentFile!!.canonicalPath)
        assertArrayEquals(sampleBytes, file.readBytes())
        pdfium.open(prepared.toUri()).use { assertEquals(3, it.pageCount) }
    }

    @Test
    fun nonPersistableGrantIsCopied() = runBlocking {
        val prepared = repository.prepare(TestDocumentsProvider.uri("sized", "sample.pdf"))
        assertTrue(prepared, File(prepared.toUri().path!!).parentFile!!.canonicalPath == copies.canonicalPath)
        assertArrayEquals(sampleBytes, File(prepared.toUri().path!!).readBytes())
    }

    @Test
    fun reopeningTheSourceKeepsTheExistingCopy() = runBlocking {
        val source = TestDocumentsProvider.uri("pipe", "sample.pdf")
        val first = repository.prepare(source)
        val file = File(first.toUri().path!!)
        // Annotations were saved into the copy since; opening the source again must not lose them.
        file.appendBytes("%annotated\n".toByteArray())
        val annotated = file.readBytes()

        assertEquals(first, repository.prepare(source))
        assertArrayEquals(annotated, file.readBytes())
    }

    @Test
    fun privateCopyIsReturnedUnchanged() = runBlocking {
        val prepared = repository.prepare(TestDocumentsProvider.uri("pipe", "sample.pdf"))
        assertEquals(prepared, repository.prepare(prepared))
    }

    @Test
    fun failedCopyLeavesNothingBehind() = runBlocking {
        val failure = runCatching { repository.prepare(TestDocumentsProvider.uri("broken", "sample.pdf")) }.exceptionOrNull()
        assertTrue("$failure", failure is IOException)
        assertTrue(copies.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun copiesAreListedAsAccessible() = runBlocking {
        val prepared = repository.prepare(TestDocumentsProvider.uri("pipe", "sample.pdf"))
        assertTrue(prepared in repository.accessibleUris())
    }

    @Test
    fun forgetDeletesOnlyPrivateCopies() = runBlocking {
        val prepared = repository.prepare(TestDocumentsProvider.uri("pipe", "sample.pdf"))
        val source = TestDocumentsProvider.uri("sized", "sample.pdf")
        repository.forget(source)
        assertTrue(File(prepared.toUri().path!!).exists())

        repository.forget(prepared)
        assertFalse(File(prepared.toUri().path!!).exists())
        assertFalse(prepared in repository.accessibleUris())
    }

    @Test
    fun concurrentCopiesOfOneSourceBothSucceed() = runBlocking {
        repeat(5) {
            val results = (0 until 2).map {
                async(Dispatchers.IO) { repository.prepare(TestDocumentsProvider.uri("pipe", "text-dense.pdf")) }
            }.awaitAll()
            val expected = context.assets.open("text-dense.pdf").use { it.readBytes() }
            results.forEach { assertArrayEquals(expected, File(it.toUri().path!!).readBytes()) }
        }
        assertTrue(copies.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun misbehavingProviderIsNotFound() = runBlocking {
        val failure = runCatching { repository.prepare(TestDocumentsProvider.uri("throwing", "sample.pdf")) }.exceptionOrNull()
        assertTrue("$failure", failure is FileNotFoundException)
    }

    @Test
    fun unreferencedCopiesAreSwept() = runBlocking {
        val hourAgo = System.currentTimeMillis() - 2 * 60 * 60 * 1000
        val kept = repository.prepare(TestDocumentsProvider.uri("pipe", "sample.pdf"))
        val orphan = repository.prepare(TestDocumentsProvider.uri("pipe", "outline.pdf"))
        val partial = File(copies, "left-over.part").apply { writeText("half") }
        listOf(File(kept.toUri().path!!), File(orphan.toUri().path!!), partial).forEach { it.setLastModified(hourAgo) }
        // Just copied for an open still in progress, not yet in the library.
        val fresh = repository.prepare(TestDocumentsProvider.uri("pipe", "text-dense.pdf"))

        repository.forgetCopiesExcept(setOf(kept))

        assertTrue(File(kept.toUri().path!!).exists())
        assertFalse(File(orphan.toUri().path!!).exists())
        assertFalse(partial.exists())
        assertTrue(File(fresh.toUri().path!!).exists())
    }

    @Test
    fun copyIsNamedAfterTheDocument() = runBlocking {
        val prepared = repository.prepare(TestDocumentsProvider.uri("pipe", "sample.pdf"))
        val name = Uri.parse(prepared).lastPathSegment!!
        assertTrue(name, Regex("""sample-[0-9a-f]{8}\.pdf""").matches(name))
    }
}
