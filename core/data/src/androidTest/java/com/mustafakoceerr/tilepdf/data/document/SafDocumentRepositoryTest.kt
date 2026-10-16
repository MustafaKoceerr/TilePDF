package com.mustafakoceerr.tilepdf.data.document

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.core.pdf.Pdfium
import kotlinx.coroutines.Dispatchers
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
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class SafDocumentRepositoryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pdfium = Pdfium(context, Dispatchers.IO.limitedParallelism(1))
    private val repository = SafDocumentRepository(context, Dispatchers.IO, pdfium)
    private val copies = File(context.filesDir, "documents")
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
    fun copyIsNamedAfterTheDocument() = runBlocking {
        val prepared = repository.prepare(TestDocumentsProvider.uri("pipe", "sample.pdf"))
        val name = Uri.parse(prepared).lastPathSegment!!
        assertTrue(name, Regex("""sample-[0-9a-f]{8}\.pdf""").matches(name))
    }
}
