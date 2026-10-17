package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.model.Bookmark
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableOfContentsSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var selectedPage: Int? = null
    private var selectedEntry: OutlineEntry? = null

    private fun show(outline: List<OutlineEntry>, bookmarks: List<Bookmark>) = compose.setContent {
        TableOfContentsSheet(
            outline = outline,
            currentIndex = -1,
            bookmarks = bookmarks,
            onSelect = { selectedEntry = it },
            onSelectPage = { selectedPage = it },
            onDismiss = {},
        )
    }

    @Test
    fun withoutOutlineBookmarksAreShown() {
        show(outline = emptyList(), bookmarks = listOf(Bookmark(4)))
        compose.onNodeWithText(context.getString(R.string.reader_page_description, 5)).performClick()
        assertEquals(4, selectedPage)
    }

    @Test
    fun noBookmarksShowsHint() {
        show(outline = emptyList(), bookmarks = emptyList())
        compose.onNodeWithText(context.getString(R.string.reader_no_bookmarks)).assertIsDisplayed()
    }

    @Test
    fun contentsComeFirstAndBookmarksAreOneTabAway() {
        val entry = OutlineEntry("1. Giriş", pageIndex = 0, topPt = 100f, depth = 0)
        show(outline = listOf(entry), bookmarks = listOf(Bookmark(8)))
        compose.onNodeWithText("1. Giriş").performClick()
        assertEquals(entry, selectedEntry)

        compose.onNodeWithText(context.getString(R.string.reader_bookmarks)).performClick()
        compose.onNodeWithText(context.getString(R.string.reader_page_description, 9)).performClick()
        assertEquals(8, selectedPage)
    }
}
