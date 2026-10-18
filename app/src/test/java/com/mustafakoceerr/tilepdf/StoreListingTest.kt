package com.mustafakoceerr.tilepdf

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Play's limits for the texts under fastlane/metadata, for every language TilePDF lists in. */
class StoreListingTest {

    private val locales = listOf("en-US", "tr-TR")

    /** Unit tests run in the module directory. */
    private fun text(locale: String, path: String): String {
        val file = File("../fastlane/metadata/android/$locale/$path")
        assertTrue("missing ${file.path}", file.isFile)
        return file.readText().trim()
    }

    private fun check(path: String, max: Int) = locales.forEach { locale ->
        val text = text(locale, path)
        assertTrue("$locale/$path is blank", text.isNotBlank())
        assertTrue("$locale/$path is ${text.length} characters, over $max", text.length <= max)
    }

    @Test
    fun titleFits() = check("title.txt", 30)

    @Test
    fun shortDescriptionFits() = check("short_description.txt", 80)

    @Test
    fun fullDescriptionFits() = check("full_description.txt", 4000)

    @Test
    fun changelogFits() = check("changelogs/1.txt", 500)
}
