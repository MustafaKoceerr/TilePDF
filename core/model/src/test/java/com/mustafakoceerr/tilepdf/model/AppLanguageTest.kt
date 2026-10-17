package com.mustafakoceerr.tilepdf.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {

    @Test
    fun regionIsIgnored() {
        assertEquals(AppLanguage.Turkish, AppLanguage.fromTag("tr-TR"))
        assertEquals(AppLanguage.English, AppLanguage.fromTag("en-GB"))
    }

    @Test
    fun emptyOrUnknownIsSystem() {
        assertEquals(AppLanguage.System, AppLanguage.fromTag(null))
        assertEquals(AppLanguage.System, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.System, AppLanguage.fromTag("de"))
    }

    @Test
    fun tagsRoundTrip() {
        AppLanguage.entries.forEach { assertEquals(it, AppLanguage.fromTag(it.tag)) }
    }
}
