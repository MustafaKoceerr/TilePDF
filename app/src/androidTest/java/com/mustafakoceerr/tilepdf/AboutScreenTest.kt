package com.mustafakoceerr.tilepdf

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.about.AboutScreen
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.mustafakoceerr.tilepdf.feature.about.R as AboutR

/** In the app module: PDFium's license assets come from core:pdf and are merged here. */
@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun showsVersionAndPdfium() {
        compose.setContent { TilePDFTheme { AboutScreen(onBack = {}, librariesRes = R.raw.aboutlibraries) } }
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        compose.onNodeWithText(context.getString(AboutR.string.about_version, version)).assertIsDisplayed()
        compose.onNodeWithText("PDFium").performClick()
        compose.onNodeWithText("The PDFium Authors", substring = true).assertIsDisplayed()
    }

    @Test
    fun opensTheLibraryList() {
        compose.setContent { TilePDFTheme { AboutScreen(onBack = {}, librariesRes = R.raw.aboutlibraries) } }
        val summary = context.getString(AboutR.string.about_libraries_summary)
        // Below PDFium's components: scroll the list to it first.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(summary))
        compose.onNodeWithText(summary).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTextCount("Material")
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextCount(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
}
