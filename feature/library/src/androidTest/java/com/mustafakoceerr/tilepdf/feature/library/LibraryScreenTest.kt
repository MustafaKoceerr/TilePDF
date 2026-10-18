package com.mustafakoceerr.tilepdf.feature.library

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private val book = LibraryItem(LibraryDocument("content://book", "Book.pdf", 10, ReadingPosition(), 1), isAccessible = true)

    private val other = LibraryItem(LibraryDocument("content://other", "Other.pdf", 3, ReadingPosition(), 0), isAccessible = true)

    private fun showAt(size: DpSize) = compose.setContent {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
            LibraryScreen(LibraryUiState.Success(listOf(book, other)), LibraryActions())
        }
    }

    private fun top(name: String) = compose.onNodeWithText(name).getBoundsInRoot().top

    @Test
    fun wideLibraryUsesColumns() {
        showAt(DpSize(1000.dp, 800.dp))
        assertEquals(top("Book.pdf"), top("Other.pdf"))
    }

    @Test
    fun phoneLibraryIsOneColumn() {
        showAt(DpSize(400.dp, 800.dp))
        assertTrue(top("Other.pdf") > top("Book.pdf"))
    }

    @Test
    fun favoriteToggleReports() {
        var toggled: String? = null
        compose.setContent {
            LibraryScreen(LibraryUiState.Success(listOf(book)), LibraryActions(onToggleFavorite = { toggled = it }))
        }
        compose.onNodeWithContentDescription(text(R.string.library_favorite_add)).performClick()
        assertEquals("content://book", toggled)
    }

    @Test
    fun emptyFavoritesHint() {
        compose.setContent {
            LibraryScreen(LibraryUiState.Success(emptyList(), filter = LibraryFilter.Favorites, hasDocuments = true), LibraryActions())
        }
        compose.onNodeWithText(text(R.string.library_no_favorites)).assertIsDisplayed()
    }

    @Test
    fun noResultsHint() {
        compose.setContent {
            LibraryScreen(LibraryUiState.Success(emptyList(), query = "xyz", searching = true, hasDocuments = true), LibraryActions())
        }
        compose.onNodeWithText(text(R.string.library_no_results)).assertIsDisplayed()
    }

    @Test
    fun removeShowsUndo() {
        var undone = false
        compose.setContent {
            LibraryScreen(
                LibraryUiState.Success(emptyList(), hasDocuments = true, pendingRemoval = "content://book"),
                LibraryActions(onUndoRemove = { undone = true }),
            )
        }
        compose.onNodeWithText(text(R.string.library_undo)).performClick()
        compose.waitForIdle()
        assertEquals(true, undone)
    }
}
