package com.mustafakoceerr.tilepdf.feature.reader

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationPalette
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationTool
import com.mustafakoceerr.tilepdf.feature.reader.annotation.AnnotationToolbar
import com.mustafakoceerr.tilepdf.feature.reader.annotation.PenWidths
import com.mustafakoceerr.tilepdf.feature.reader.annotation.ToolbarState
import com.mustafakoceerr.tilepdf.feature.reader.components.ReaderErrorContent
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Touch targets, labels and contrast, as the Accessibility Test Framework checks them. */
@RunWith(AndroidJUnit4::class)
class ReaderAccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        assumeTrue(Build.VERSION.SDK_INT >= 34)
        compose.enableAccessibilityChecks()
    }

    @Test
    fun errorScreensPass() {
        var error by mutableStateOf(ReaderError.entries.first())
        compose.setContent { TilePDFTheme { ReaderErrorContent(error, onFileChosen = {}, onRetry = {}, onBack = {}) } }
        ReaderError.entries.forEach {
            error = it
            compose.waitForIdle()
            compose.onRoot().tryPerformAccessibilityChecks()
        }
    }

    @Test
    fun penToolbarPasses() {
        compose.setContent {
            TilePDFTheme {
                AnnotationToolbar(
                    state = ToolbarState(
                        tool = AnnotationTool.Pen,
                        markupColor = AnnotationPalette[0],
                        penColor = AnnotationPalette[2],
                        penWidthPt = PenWidths[1],
                        canUndo = true,
                        canRedo = false,
                    ),
                    onTool = {}, onMarkupColor = {}, onPenColor = {}, onPenWidth = {}, onUndo = {}, onRedo = {}, onDone = {},
                )
            }
        }
        compose.onRoot().tryPerformAccessibilityChecks()
    }
}
