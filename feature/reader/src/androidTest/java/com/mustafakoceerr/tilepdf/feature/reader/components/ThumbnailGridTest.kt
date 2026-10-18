package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class ThumbnailGridTest {

    @get:Rule
    val compose = createComposeRule()

    private val a4 = PageSize(595, 842)
    private val requested: MutableSet<Int> = Collections.synchronizedSet(mutableSetOf())
    private var selected: Int? = null

    private fun show(pageCount: Int, currentPage: Int = 0) = compose.setContent {
        ThumbnailGrid(
            pageSizes = List(pageCount) { a4 },
            currentPage = currentPage,
            thumbnail = { page, widthPx ->
                requested += page
                ImageBitmap(widthPx, a4.heightForWidth(widthPx))
            },
            onSelect = { selected = it },
        )
    }

    @Test
    fun tappingSelectsPage() {
        show(pageCount = 12)
        compose.onNodeWithText("3").performClick()
        assertEquals(2, selected)
    }

    @Test
    fun currentPageIsMarked() {
        show(pageCount = 12, currentPage = 4)
        compose.onNodeWithText("5", useUnmergedTree = false).assertIsSelected()
    }

    @Test
    fun startsAtTheCurrentPage() {
        show(pageCount = 500, currentPage = 300)
        compose.waitForIdle()
        assertTrue("page 301 is requested", 300 in requested)
        assertTrue("page 1 is not", 0 !in requested)
    }

    @Test
    fun offscreenItemsAreNotRequested() {
        show(pageCount = 500)
        compose.waitForIdle()
        // A screenful and at most a row or two of prefetch; never the document.
        assertTrue("requested ${requested.size} pages", requested.isNotEmpty() && requested.size < 40)
        assertTrue(requested.all { it < 40 })
    }
}
