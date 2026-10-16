package com.mustafakoceerr.tilepdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.library.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real app: catches a Hilt graph or resource split that only fails at runtime. */
@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesIntoLibrary() {
        val title = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.library_title)
        compose.onNodeWithText(title).assertIsDisplayed()
    }
}
