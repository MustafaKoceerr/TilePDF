package com.mustafakoceerr.tilepdf.data.text

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CachingDocumentTextRepositoryTest {

    private val repository = CachingDocumentTextRepository(UnconfinedTestDispatcher())

    @Test
    fun searchEmitsMatchesPerPage() = runTest {
        val document = FakePdfDocument(listOf("a tile", "none", "tile tile"))
        repository.search(document, "TILE").test {
            val first = awaitItem()
            assertEquals(listOf(0), first.map { it.pageIndex })
            val second = awaitItem()
            assertEquals(listOf(2, 2), second.map { it.pageIndex })
            awaitComplete()
        }
    }

    @Test
    fun textIsExtractedOncePerPage() = runTest {
        val document = FakePdfDocument(listOf("tile", "page", "tile"))
        repository.search(document, "tile").test { cancelAndConsumeRemainingEvents() }
        repository.search(document, "page").test { cancelAndConsumeRemainingEvents() }
        repository.pageText(document, 1)
        assertEquals(3, document.pageTextCalls)
    }

    @Test
    fun anotherDocumentReplacesTheCache() = runTest {
        val first = FakePdfDocument(listOf("alpha"))
        val second = FakePdfDocument(listOf("beta"))
        repository.search(first, "alpha").test { awaitItem(); awaitComplete() }

        repository.search(second, "alpha").test { awaitComplete() }
        repository.search(second, "beta").test {
            assertEquals(1, awaitItem().size)
            awaitComplete()
        }
        assertEquals(1, second.pageTextCalls)
    }

    @Test
    fun searchStopsWhenDocumentCloses() = runTest {
        val document = FakePdfDocument(listOf("tile", "tile", "tile"))
        document.beforePageText = { pageIndex -> if (pageIndex == 1) document.close() }
        repository.search(document, "tile").test {
            assertEquals(1, awaitItem().size)
            assertTrue(awaitError() is IllegalStateException)
        }
    }

    @Test
    fun blankQueryEmitsNothing() = runTest {
        val document = FakePdfDocument(listOf("tile"))
        repository.search(document, "  ").test { awaitComplete() }
        assertEquals(0, document.pageTextCalls)
    }
}
