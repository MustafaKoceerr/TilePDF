package com.mustafakoceerr.tilepdf.feature.reader.annotation

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.reader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnnotationToolbarTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)
    private var tool: AnnotationTool? = null

    private fun show(state: ToolbarState) = compose.setContent {
        AnnotationToolbar(
            state = state,
            onTool = { tool = it },
            onMarkupColor = {}, onPenColor = {}, onPenWidth = {},
            onUndo = {}, onRedo = {}, onDone = {},
        )
    }

    private fun state(tool: AnnotationTool = AnnotationTool.Select, canUndo: Boolean = false) =
        ToolbarState(tool, AnnotationPalette[0], AnnotationPalette[4], 2f, canUndo = canUndo, canRedo = false)

    @Test
    fun selectingPenReportsIt() {
        show(state())
        compose.onNodeWithContentDescription(text(R.string.annotation_tool_pen)).performClick()
        assertEquals(AnnotationTool.Pen, tool)
    }

    @Test
    fun widthsOnlyForPen() {
        show(state(AnnotationTool.Select))
        assertEquals(0, compose.onAllNodesWithContentDescription(text(R.string.annotation_width), substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun penShowsThreeWidths() {
        show(state(AnnotationTool.Pen))
        assertEquals(3, compose.onAllNodesWithContentDescription(text(R.string.annotation_width), substring = true).fetchSemanticsNodes().size)
    }

    @Test
    fun undoDisabledWithoutHistory() {
        show(state(canUndo = false))
        compose.onNodeWithContentDescription(text(R.string.annotation_undo)).assertIsNotEnabled()
    }
}
