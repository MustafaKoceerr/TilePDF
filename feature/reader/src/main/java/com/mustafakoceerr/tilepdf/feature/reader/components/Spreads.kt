package com.mustafakoceerr.tilepdf.feature.reader.components

/**
 * Pages paired as a printed book shows them: the cover alone on the right,
 * then each left page with the right page after it.
 */
internal fun spreads(pageCount: Int): List<List<Int>> {
    if (pageCount <= 0) return emptyList()
    return listOf(listOf(0)) + (1 until pageCount).chunked(2)
}

/** The spread holding [pageIndex]; past either end, the nearest spread. */
internal fun spreadOf(pageIndex: Int, spreads: List<List<Int>>): Int {
    val index = spreads.indexOfFirst { pageIndex in it }
    return when {
        index >= 0 -> index
        pageIndex < 0 -> 0
        else -> spreads.lastIndex
    }
}

/** Material 3's expanded width: below it, two pages side by side are too small to read. */
private const val SPREAD_MIN_WIDTH_DP = 840f

/** Whether the reader shows spreads in the space it has, landscape and wide; [enabled] by the setting. */
internal fun showsSpreads(enabled: Boolean, widthDp: Float, heightDp: Float): Boolean =
    enabled && widthDp >= SPREAD_MIN_WIDTH_DP && widthDp > heightDp
