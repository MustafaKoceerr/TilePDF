package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.lazy.LazyListLayoutInfo
import com.mustafakoceerr.tilepdf.core.geometry.PageLayout
import com.mustafakoceerr.tilepdf.core.geometry.Viewport
import com.mustafakoceerr.tilepdf.core.geometry.VisiblePage
import kotlin.math.roundToInt

/**
 * Derives the [PageLayout] of the zoomable page list. The list reports only
 * vertical item positions, so the horizontal ones are recomputed here using
 * the same rounding as the layout itself — any mismatch would shift tiles by a
 * pixel against the page they're drawn on.
 *
 * @param horizontalPaddingPx the list's horizontal content padding, already
 *   rounded to pixels as LazyColumn does (`Dp.roundToPx()`).
 */
fun pageLayout(zoomState: ZoomState, horizontalPaddingPx: Int): PageLayout {
    val listWidth = (zoomState.viewportWidth * zoomState.scale).roundToInt()
    return PageLayout(
        pageLeft = horizontalPaddingPx - zoomState.panX.roundToInt(),
        pageWidth = listWidth - 2 * horizontalPaddingPx,
    )
}

/**
 * The pages currently on screen and which part of each is visible. Assumes
 * item keys are page indices, as in the reader's page list.
 *
 * @param viewportWidth the on-screen width. Not `viewportSize.width`: when
 *   zoomed, the list is measured wider than the screen.
 */
fun LazyListLayoutInfo.visiblePages(pageLayout: PageLayout, viewportWidth: Int): List<VisiblePage> {
    val viewport = Viewport(viewportWidth, viewportSize.height)
    // Item offsets are measured from the end of the top content padding
    // (viewportStartOffset is minus that padding); convert to viewport y.
    return visibleItemsInfo.mapNotNull { item ->
        val top = item.offset - viewportStartOffset
        viewport.visiblePage(item.key as Int, pageLayout.pageBounds(top, item.size))
    }
}
