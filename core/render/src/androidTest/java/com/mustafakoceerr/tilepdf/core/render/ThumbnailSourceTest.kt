package com.mustafakoceerr.tilepdf.core.render

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mustafakoceerr.tilepdf.core.pdf.PdfDocument
import com.mustafakoceerr.tilepdf.testing.TestAssets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThumbnailSourceTest {

    private lateinit var document: CountingDocument
    private lateinit var source: ThumbnailSource

    @Before
    fun setUp() = runBlocking {
        document = CountingDocument(TestAssets.openDocument("sample.pdf"))
        source = ThumbnailSource(document, PageRenderer(Dispatchers.IO))
    }

    @After
    fun tearDown() = document.close()

    @Test
    fun secondRequestIsCached() = runBlocking {
        source.thumbnail(pageIndex = 0, widthPx = 200)
        source.thumbnail(pageIndex = 0, widthPx = 200)
        assertEquals(1, document.renders)
    }

    @Test
    fun sizeMatchesWidth() = runBlocking {
        val page = document.pageSizes[0]
        val thumbnail = source.thumbnail(pageIndex = 0, widthPx = 200)
        assertEquals(200, thumbnail.width)
        assertEquals(page.heightForWidth(200), thumbnail.height)
    }

    @Test
    fun anotherWidthRendersAgain() = runBlocking {
        source.thumbnail(pageIndex = 0, widthPx = 200)
        assertEquals(300, source.thumbnail(pageIndex = 0, widthPx = 300).width)
        assertEquals(2, document.renders)
    }

    @Test
    fun trimmingDropsTheCache() = runBlocking {
        source.thumbnail(pageIndex = 0, widthPx = 200)
        source.trimMemory()
        source.thumbnail(pageIndex = 0, widthPx = 200)
        assertEquals(2, document.renders)
    }

    private class CountingDocument(private val real: PdfDocument) : PdfDocument by real {
        var renders = 0

        override suspend fun render(pageIndex: Int, bitmap: Bitmap, transform: Matrix) {
            renders++
            real.render(pageIndex, bitmap, transform)
        }
    }
}
