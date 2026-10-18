package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.reader.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private var submitted: String? = null
    private var cancelled = false

    private fun show(wrongPassword: Boolean = false) = compose.setContent {
        PasswordDialog(wrongPassword, onSubmit = { submitted = it }, onCancel = { cancelled = true })
    }

    @Test
    fun submitSendsTypedPassword() {
        show()
        compose.onNodeWithTag(PASSWORD_FIELD_TAG).performTextInput("secret")
        compose.onNodeWithText(text(R.string.reader_password_open)).performClick()
        assertEquals("secret", submitted)
    }

    @Test
    fun doneKeySubmits() {
        show()
        compose.onNodeWithTag(PASSWORD_FIELD_TAG).performTextInput("secret")
        compose.onNodeWithTag(PASSWORD_FIELD_TAG).performImeAction()
        assertEquals("secret", submitted)
    }

    @Test
    fun wrongPasswordShowsMessage() {
        show(wrongPassword = true)
        compose.onNodeWithText(text(R.string.reader_password_wrong)).assertIsDisplayed()
    }

    @Test
    fun cancelCallsOnCancel() {
        show()
        compose.onNodeWithText(text(R.string.cancel)).performClick()
        assertTrue(cancelled)
    }

    @Test
    fun passwordDialogSurvivesRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { PasswordDialog(false, onSubmit = { submitted = it }, onCancel = {}) }
        compose.onNodeWithTag(PASSWORD_FIELD_TAG).performTextInput("sec")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(text(R.string.reader_password_open)).performClick()
        assertEquals("sec", submitted)
        compose.onNodeWithTag(PASSWORD_FIELD_TAG).assertTextContains("•••", substring = true)
    }
}
