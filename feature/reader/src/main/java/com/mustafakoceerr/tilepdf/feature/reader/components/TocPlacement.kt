package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.ui.unit.dp

/** Where contents, bookmarks and annotations open. */
enum class TocPlacement {
    /** A bottom sheet over the pages; closes once something is chosen. */
    Sheet,

    /** A panel beside the pages that stays open while reading. */
    Side,
}

/** Material 3's expanded width; below it a panel leaves the pages too narrow. */
private const val SIDE_MIN_WIDTH_DP = 840f

/** Below this the height is compact, as on a phone in landscape. */
private const val SIDE_MIN_HEIGHT_DP = 480f

internal val TocPanelWidth = 360.dp

/** From the space the reader has, not the device, so split screen behaves too. */
internal fun tocPlacement(widthDp: Float, heightDp: Float): TocPlacement =
    if (widthDp >= SIDE_MIN_WIDTH_DP && heightDp >= SIDE_MIN_HEIGHT_DP) TocPlacement.Side else TocPlacement.Sheet
