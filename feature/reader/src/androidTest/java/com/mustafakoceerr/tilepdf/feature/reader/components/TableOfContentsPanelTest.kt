package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.model.OutlineEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TableOfContentsPanelTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val entry = OutlineEntry("1. Giriş", pageIndex = 0, topPt = 100f, depth = 0)
    private var selected: OutlineEntry? = null
    private var closed = false

    private fun show() = compose.setContent {
        TableOfContentsPanel(
            outline = listOf(entry),
            currentIndex = 0,
            bookmarks = emptyList(),
            onSelect = { selected = it },
            onSelectPage = {},
            onClose = { closed = true },
        )
    }

    @Test
    fun selectingKeepsPanelOpen() {
        show()
        compose.onNodeWithText("1. Giriş").performClick()
        assertEquals(entry, selected)
        assertEquals(false, closed)
        compose.onNodeWithText(context.getString(R.string.reader_toc)).assertIsDisplayed()
    }

    @Test
    fun closeButtonCloses() {
        show()
        compose.onNodeWithContentDescription(context.getString(R.string.reader_toc_close)).performClick()
        assertTrue(closed)
    }
}
