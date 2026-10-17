package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.feature.reader.ReaderError
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderErrorContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    @Test
    fun permissionLostOffersChooseFile() {
        compose.setContent { ReaderErrorContent(ReaderError.PermissionLost, onFileChosen = {}, onRetry = {}, onBack = {}) }
        compose.onNodeWithText(text(R.string.error_permission_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.error_choose_file)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.error_back_to_library)).assertIsDisplayed()
    }

    @Test
    fun retryCallsBack() {
        var retried = false
        compose.setContent { ReaderErrorContent(ReaderError.OutOfMemory, onFileChosen = {}, onRetry = { retried = true }, onBack = {}) }
        compose.onNodeWithText(text(R.string.error_try_again)).performClick()
        assertTrue(retried)
    }
}
