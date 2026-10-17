package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.mustafakoceerr.tilepdf.core.geometry.PixelRect
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import kotlin.math.roundToInt

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * The largest rect with the page's aspect ratio that fits in [viewport],
 * centred. Heights come from [PageSize.heightForWidth] like every renderer's.
 */
internal fun fitPage(pageSize: PageSize, viewport: IntSize): PixelRect {
    val width = if (viewport.width / pageSize.aspectRatio <= viewport.height) {
        viewport.width
    } else {
        (viewport.height * pageSize.aspectRatio).roundToInt()
    }
    val height = pageSize.heightForWidth(width)
    return PixelRect.ofSize((viewport.width - width) / 2, (viewport.height - height) / 2, width, height)
}

/**
 * Zoom and pan of one page shown on its own, as in horizontal mode.
 *
 * All positions are viewport pixels. The page is laid out at [fitted] and
 * transformed around its top-left corner: its pixel `p` is drawn at
 * `fitted.topLeft + offset + p * scale`. A page smaller than the viewport
 * along an axis is centred there; a bigger one can't show past its edges.
 */
class PageZoom(val fitted: PixelRect, val viewport: IntSize) {

    var scale by mutableFloatStateOf(MIN_SCALE)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    private val origin = Offset(fitted.left.toFloat(), fitted.top.toFloat())

    /** Applies one frame of a pinch: the content under [centroid] stays put, then follows [pan]. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
        val step = newScale / scale
        scale = newScale
        offset = clamp((offset + origin - centroid) * step + centroid - origin + pan)
    }

    /** Zooms in around [focus], or back out to the whole page. */
    fun doubleTap(focus: Offset) {
        if (scale > MIN_SCALE + 0.01f) {
            scale = MIN_SCALE
            offset = clamp(Offset.Zero)
        } else {
            transform(focus, Offset.Zero, DOUBLE_TAP_SCALE / scale)
        }
    }

    /** Pans by a drag [delta]; returns the part of it actually applied. */
    fun panBy(delta: Offset): Offset {
        val old = offset
        offset = clamp(offset + delta)
        return offset - old
    }

    /** Where the page is on screen, sized as tiles are requested for it. */
    fun pageBounds(): PixelRect = PixelRect.ofSize(
        (origin.x + offset.x).roundToInt(),
        (origin.y + offset.y).roundToInt(),
        (fitted.width * scale).roundToInt(),
        (fitted.height * scale).roundToInt(),
    )

    /** The page pixel under a viewport [position], or `null` off the page. */
    fun toPagePixels(position: Offset): Offset? {
        val local = (position - origin - offset) / scale
        val onPage = local.x in 0f..fitted.width.toFloat() && local.y in 0f..fitted.height.toFloat()
        return local.takeIf { onPage }
    }

    private fun clamp(offset: Offset) = Offset(
        clampAxis(offset.x, origin.x, fitted.width * scale, viewport.width.toFloat()),
        clampAxis(offset.y, origin.y, fitted.height * scale, viewport.height.toFloat()),
    )

    /** Offset along one axis that keeps a page of [size] at [start] centred or covering the viewport. */
    private fun clampAxis(offset: Float, start: Float, size: Float, viewport: Float): Float =
        if (size <= viewport) {
            (viewport - size) / 2f - start
        } else {
            offset.coerceIn(viewport - size - start, -start)
        }
}
