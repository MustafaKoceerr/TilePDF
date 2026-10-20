package com.mustafakoceerr.tilepdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Play's limits for the texts under fastlane/metadata, for every language
 * TilePDF lists in, and for the graphics, which only the default language has.
 */
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

    private val images = File("../fastlane/metadata/android/en-US/images")

    private fun pngs(dir: String) = File(images, dir).listFiles { f -> f.extension == "png" }.orEmpty().toList()

    private fun image(file: File) = requireNotNull(ImageIO.read(file)) { "unreadable ${file.path}" }

    @Test
    fun iconIsA512SquarePngUnder1Mb() {
        val icon = File(images, "icon.png")
        val read = image(icon)
        assertEquals(512 to 512, read.width to read.height)
        assertTrue("icon is ${icon.length()} bytes", icon.length() <= 1_024 * 1_024)
    }

    @Test
    fun featureGraphicIs1024By500WithoutAlpha() {
        val graphic = image(File(images, "featureGraphic.png"))
        assertEquals(1024 to 500, graphic.width to graphic.height)
        assertFalse("featureGraphic has alpha", graphic.colorModel.hasAlpha())
    }

    @Test
    fun phoneHasTwoToEightScreenshots() = assertTrue(pngs("phoneScreenshots").size in 2..8)

    @Test
    fun tabletsHaveAtMostEightScreenshots() = listOf("sevenInchScreenshots", "tenInchScreenshots").forEach {
        assertTrue("$it has ${pngs(it).size}", pngs(it).size in 1..8)
    }

    @Test
    fun screenshotsMeetPlaysLimits() = listOf("phoneScreenshots", "sevenInchScreenshots", "tenInchScreenshots").forEach { dir ->
        pngs(dir).forEach { file ->
            val shot = image(file)
            val short = minOf(shot.width, shot.height)
            val long = maxOf(shot.width, shot.height)
            val name = "$dir/${file.name}"
            assertFalse("$name has alpha", shot.colorModel.hasAlpha())
            assertTrue("$name is ${shot.width}x${shot.height}", short >= 320 && long <= 3840)
            assertTrue("$name is longer than twice its width", long <= 2 * short)
            assertTrue("$name is over 8 MB", file.length() <= 8L * 1_024 * 1_024)
        }
    }
}
