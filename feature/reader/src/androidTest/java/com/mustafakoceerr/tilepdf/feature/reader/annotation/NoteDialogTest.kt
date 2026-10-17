package com.mustafakoceerr.tilepdf.feature.reader.annotation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.reader.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var saved: String? = null

    private fun show(initial: String = "", editing: Boolean = false) = compose.setContent {
        NoteDialog(initial, editing, onSave = { saved = it }, onDelete = {}, onDismiss = {})
    }

    @Test
    fun savingReportsText() {
        show()
        compose.onNodeWithTag(NOTE_FIELD_TAG).performTextInput("Önemli kısım")
        compose.onNodeWithText(context.getString(R.string.annotation_save)).performClick()
        assertEquals("Önemli kısım", saved)
    }

    @Test
    fun editingStartsFromTheNote() {
        show(initial = "eski", editing = true)
        compose.onNodeWithText(context.getString(R.string.annotation_save)).performClick()
        assertEquals("eski", saved)
    }
}
