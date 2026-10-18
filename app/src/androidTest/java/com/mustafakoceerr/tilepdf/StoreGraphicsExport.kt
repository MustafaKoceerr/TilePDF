package com.mustafakoceerr.tilepdf

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders Play's store graphics from the app's own icon layers, so they never
 * drift from the launcher icon. Not a check: run it on purpose, then
 * `adb pull /sdcard/Android/data/com.mustafakoceerr.tilepdf/files/store`.
 */
@RunWith(AndroidJUnit4::class)
class StoreGraphicsExport {

    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val outDir = File(context.getExternalFilesDir(null), "store").apply { mkdirs() }

    @Test
    fun icon() = export("icon.png", 512, 512) { size ->
        Box(Modifier.size(size)) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
    }

    @Test
    fun featureGraphicEnglish() = featureGraphic("featureGraphic-en-US.png", "Fast, private PDF reader with notes.")

    @Test
    fun featureGraphicTurkish() = featureGraphic("featureGraphic-tr-TR.png", "Hızlı ve gizli PDF okuyucu, notlarla.")

    private fun featureGraphic(name: String, tagline: String) = export(name, 1024, 500) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.background(Color(0xFF3F51B5)).padding(horizontal = pxToDp(56)),
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(pxToDp(420)))
            Column {
                Text("TilePDF", color = Color.White, fontSize = pxToSp(110), fontWeight = FontWeight.Bold)
                Text(tagline, color = Color(0xFFE8EAF6), fontSize = pxToSp(40))
            }
        }
    }

    /** Draws [content] at exactly [widthPx] × [heightPx] and saves it as a PNG. */
    private fun export(name: String, widthPx: Int, heightPx: Int, content: @Composable (Dp) -> Unit) {
        compose.setContent {
            val density = LocalDensity.current
            val width = with(density) { widthPx.toDp() }
            val height = with(density) { heightPx.toDp() }
            Box(Modifier.size(width, height).testTag("graphic")) { content(width) }
        }
        val bitmap = compose.onNodeWithTag("graphic").captureToImage().asAndroidBitmap()
        assertEquals(widthPx, bitmap.width)
        assertEquals(heightPx, bitmap.height)
        File(outDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Composable
    private fun pxToDp(px: Int) = with(LocalDensity.current) { px.toDp() }

    @Composable
    private fun pxToSp(px: Int) = with(LocalDensity.current) { px.toSp() }
}
