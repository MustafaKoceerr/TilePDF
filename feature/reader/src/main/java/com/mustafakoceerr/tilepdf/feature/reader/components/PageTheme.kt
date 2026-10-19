package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.mustafakoceerr.tilepdf.model.PageTheme

private val SepiaPaper = Color(0xFFF4ECD8)
private val GreenPaper = Color(0xFFCCE8CF)
private val DimPaper = Color(0xFF2B2B2B)
private val DimInk = Color(0xFFC8C8C8)
private val WarmNightPaper = Color(0xFF2A2118)
private val WarmNightInk = Color(0xFFE8D9C0)

/** Inverts colours without re-rendering: white paper becomes black. */
private val NightMatrix = floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)

/** Multiplies each channel by [paper]: white becomes paper, black stays black. */
private fun tintMatrix(paper: Color) = floatArrayOf(
    paper.red, 0f, 0f, 0f, 0f,
    0f, paper.green, 0f, 0f, 0f,
    0f, 0f, paper.blue, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/**
 * Inverts into a dark [paper] with [ink] for text: white becomes paper and
 * black becomes ink, so text is never pure white on pure black.
 */
private fun darkMatrix(paper: Color, ink: Color) = floatArrayOf(
    paper.red - ink.red, 0f, 0f, 0f, ink.red * 255,
    0f, paper.green - ink.green, 0f, 0f, ink.green * 255,
    0f, 0f, paper.blue - ink.blue, 0f, ink.blue * 255,
    0f, 0f, 0f, 1f, 0f,
)

/** How rendered pages are recoloured for this theme; `null` draws them as rendered. */
internal fun PageTheme.colorMatrix(): ColorMatrix? = when (this) {
    PageTheme.Light -> null
    PageTheme.Night -> ColorMatrix(NightMatrix.copyOf())
    PageTheme.Sepia -> ColorMatrix(tintMatrix(SepiaPaper))
    PageTheme.Green -> ColorMatrix(tintMatrix(GreenPaper))
    PageTheme.Dim -> ColorMatrix(darkMatrix(DimPaper, DimInk))
    PageTheme.WarmNight -> ColorMatrix(darkMatrix(WarmNightPaper, WarmNightInk))
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
        PageTheme.Green -> GreenPaper
        PageTheme.Dim -> DimPaper
        PageTheme.WarmNight -> WarmNightPaper
    }
