package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.mustafakoceerr.tilepdf.model.PageTheme

private val SepiaPaper = Color(0xFFF4ECD8)

/** Inverts colours without re-rendering: white paper becomes black. */
private val NightMatrix = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** Multiplies each channel by the paper colour: white becomes paper, black stays black. */
private val SepiaMatrix = floatArrayOf(
    SepiaPaper.red, 0f, 0f, 0f, 0f,
    0f, SepiaPaper.green, 0f, 0f, 0f,
    0f, 0f, SepiaPaper.blue, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** How rendered pages are recoloured for this theme; `null` draws them as rendered. */
internal fun PageTheme.colorMatrix(): ColorMatrix? = when (this) {
    PageTheme.Light -> null
    PageTheme.Night -> ColorMatrix(NightMatrix.copyOf())
    PageTheme.Sepia -> ColorMatrix(SepiaMatrix.copyOf())
}

internal fun PageTheme.colorFilter(): ColorFilter? = colorMatrix()?.let(ColorFilter::colorMatrix)

/** An ARGB [color] as this theme draws pages: what Compose-drawn annotations use. Alpha is kept. */
internal fun PageTheme.recolor(color: Int): Int {
    val m = colorMatrix()?.values ?: return color
    val rgb = intArrayOf((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF)
    val channels = IntArray(3) { row ->
        (m[row * 5] * rgb[0] + m[row * 5 + 1] * rgb[1] + m[row * 5 + 2] * rgb[2] + m[row * 5 + 3] * 255 + m[row * 5 + 4])
            .toInt().coerceIn(0, 255)
    }
    return (color and 0xFF000000.toInt()) or (channels[0] shl 16) or (channels[1] shl 8) or channels[2]
}

/** Shown under a page until its image arrives. */
internal val PageTheme.paperColor: Color
    get() = when (this) {
        PageTheme.Light -> Color.White
        PageTheme.Night -> Color.Black
        PageTheme.Sepia -> SepiaPaper
    }
