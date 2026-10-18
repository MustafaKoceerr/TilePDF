package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.graphics.Matrix
import com.mustafakoceerr.tilepdf.core.geometry.PageViewTransform

/** Page points to laid-out pixels: through the turn and crop, then [scale]. */
internal fun PageViewTransform.drawMatrix(scale: Float): Matrix = Matrix().apply {
    val m = affine
    // Compose's column-major layout: x' = [0,0]·x + [1,0]·y + [3,0].
    this[0, 0] = m.a * scale
    this[0, 1] = m.b * scale
    this[1, 0] = m.c * scale
    this[1, 1] = m.d * scale
    this[3, 0] = m.tx * scale
    this[3, 1] = m.ty * scale
}
