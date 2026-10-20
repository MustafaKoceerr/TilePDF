package com.mustafakoceerr.tilepdf.core.designsystem

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Wider than this, rows of text and switches get hard to follow across. */
private val ReadableWidth = 640.dp

/** The full width on a phone; on a tablet or in landscape, no wider than reads well. */
fun Modifier.readableWidth(): Modifier = widthIn(max = ReadableWidth).fillMaxWidth()
