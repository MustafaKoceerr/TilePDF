package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReaderPreferences
import com.mustafakoceerr.tilepdf.model.ReadingMode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderSettingsSheetTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun text(id: Int) = context.getString(id)

    private var theme: PageTheme? = null
    private var mode: ReadingMode? = null
    private var keepScreenOn: Boolean? = null

    private fun show(preferences: ReaderPreferences = ReaderPreferences()) = compose.setContent {
        ReaderSettingsSheet(
            preferences = preferences,
            onPageThemeSelected = { theme = it },
            onReadingModeSelected = { mode = it },
            onKeepScreenOnChange = { keepScreenOn = it },
            onVolumeKeysChange = {},
            onDismiss = {},
        )
    }

    @Test
    fun selectingSepiaReportsIt() {
        show()
        compose.onNodeWithText(text(R.string.reader_theme_sepia)).performClick()
        assertEquals(PageTheme.Sepia, theme)
    }

    @Test
    fun selectedThemeIsMarked() {
        show(ReaderPreferences(pageTheme = PageTheme.Night))
        compose.onNodeWithText(text(R.string.reader_theme_night)).assertIsSelected()
        compose.onNodeWithText(text(R.string.reader_theme_light)).assertIsNotSelected()
    }

    @Test
    fun allSixThemesAreOffered() {
        show()
        listOf(
            R.string.reader_theme_light, R.string.reader_theme_sepia, R.string.reader_theme_green,
            R.string.reader_theme_dim, R.string.reader_theme_warm_night, R.string.reader_theme_night,
        ).forEach { compose.onNodeWithText(text(it)).assertIsDisplayed() }
    }

    @Test
    fun selectingDimReportsIt() {
        show()
        compose.onNodeWithText(text(R.string.reader_theme_dim)).performClick()
        assertEquals(PageTheme.Dim, theme)
    }

    @Test
    fun aNewThemeIsMarkedWhenChosen() {
        show(ReaderPreferences(pageTheme = PageTheme.WarmNight))
        compose.onNodeWithText(text(R.string.reader_theme_warm_night)).assertIsSelected()
        compose.onNodeWithText(text(R.string.reader_theme_night)).assertIsNotSelected()
    }

    @Test
    fun keepScreenOnSwitchReportsChange() {
        show()
        compose.onNodeWithText(text(R.string.reader_keep_screen_on)).performClick()
        assertEquals(true, keepScreenOn)
    }

    @Test
    fun selectingHorizontalReportsIt() {
        show()
        compose.onNodeWithText(text(R.string.reader_layout_horizontal)).performClick()
        assertEquals(ReadingMode.Horizontal, mode)
    }
}
