package com.mustafakoceerr.tilepdf.feature.library

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Touch targets, labels and contrast, as the Accessibility Test Framework checks them. */
@RunWith(AndroidJUnit4::class)
class LibraryAccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private val items = listOf(
        LibraryItem(LibraryDocument("content://a", "Favorite.pdf", 10, ReadingPosition(), 2, favorite = true), isAccessible = true),
        LibraryItem(LibraryDocument("content://b", "Moved.pdf", 4, ReadingPosition(), 1), isAccessible = false),
    )

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        compose.enableAccessibilityChecks()
    }

    @Test
    fun listPasses() {
        compose.setContent { TilePDFTheme { LibraryScreen(LibraryUiState.Success(items), LibraryActions()) } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }

    @Test
    fun emptyPasses() {
        compose.setContent { TilePDFTheme { LibraryScreen(LibraryUiState.Success(emptyList()), LibraryActions()) } }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
