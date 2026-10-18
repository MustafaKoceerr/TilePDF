package com.mustafakoceerr.tilepdf.feature.settings

import com.mustafakoceerr.languagemanager.LanguageMode
import com.mustafakoceerr.tilepdf.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageMappingTest {

    @Test
    fun systemDefaultIsSystem() = assertEquals(AppLanguage.System, LanguageMode.SystemDefault.toAppLanguage())

    @Test
    fun customTagsMapByLanguage() {
        assertEquals(AppLanguage.Turkish, LanguageMode.Custom("tr-TR").toAppLanguage())
        assertEquals(AppLanguage.English, LanguageMode.Custom("en").toAppLanguage())
    }

    @Test
    fun unknownTagIsSystem() = assertEquals(AppLanguage.System, LanguageMode.Custom("de").toAppLanguage())

    @Test
    fun roundTrip() {
        AppLanguage.entries.forEach { assertEquals(it, it.toLanguageMode().toAppLanguage()) }
        assertEquals(LanguageMode.SystemDefault, AppLanguage.System.toLanguageMode())
        assertEquals(LanguageMode.Custom("tr"), AppLanguage.Turkish.toLanguageMode())
    }
}
