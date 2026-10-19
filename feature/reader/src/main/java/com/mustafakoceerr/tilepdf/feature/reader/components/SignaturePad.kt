package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.mustafakoceerr.tilepdf.core.geometry.PdfPoint
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.model.Signature

/** The pad's width to height: room for a name written out. */
private const val PAD_ASPECT = 2.5f

/** The share of the pad a saved signature fills, leaving a margin. */
private const val SAVED_FILL = 0.8f

private val InkColor = Color.Black

/**
 * Draws a signature, starting from [saved] when there is one. "Place"
 * hands the strokes, in pad pixels, on to be placed on the page.
 */
@Composable
internal fun SignaturePadDialog(saved: Signature?, onPlace: (List<List<PdfPoint>>) -> Unit, onDismiss: () -> Unit) {
    var padWidth by remember { mutableFloatStateOf(0f) }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // The saved signature, fitted into the pad once its size is known.
    var seeded by remember { mutableStateOf(saved == null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.signature_title), style = MaterialTheme.typography.headlineSmall)
                val description = stringResource(R.string.signature_pad_description)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(PAD_ASPECT)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                        .semantics { contentDescription = description }
                        .onSizeChanged { size ->
                            padWidth = size.width.toFloat()
                            if (!seeded && saved != null) {
                                strokes += saved.inPad(size.width.toFloat(), size.height.toFloat())
                                seeded = true
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    current = current + change.position
                                },
                                onDragEnd = {
                                    strokes += current
                                    current = emptyList()
                                },
                                onDragCancel = { current = emptyList() },
                            )
                        },
                ) {
                    val width = (padWidth / 150f).coerceAtLeast(2f)
                    (strokes + listOf(current)).filter { it.isNotEmpty() }.forEach { stroke ->
                        val path = Path().apply {
                            moveTo(stroke[0].x, stroke[0].y)
                            stroke.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(path, InkColor, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) {
                        Text(stringResource(R.string.signature_clear))
                    }
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        TextButton(
                            onClick = { onPlace(strokes.map { stroke -> stroke.map { PdfPoint(it.x, it.y) } }) },
                            enabled = strokes.isNotEmpty(),
                        ) { Text(stringResource(R.string.signature_place)) }
                    }
                }
            }
        }
    }
}

/** The signature as pad pixels, as wide as fits with a margin, centred. */
private fun Signature.inPad(padWidth: Float, padHeight: Float): List<List<Offset>> {
    val scale = minOf(padWidth * SAVED_FILL, padHeight * SAVED_FILL / height.coerceAtLeast(0.01f))
    val left = (padWidth - scale) / 2
    val top = (padHeight - height * scale) / 2
    return strokes.map { stroke -> stroke.map { Offset(left + it.x * scale, top + it.y * scale) } }
}

/** Shown while a signature waits for a tap on the page. */
@Composable
internal fun SignatureHint(onCancel: () -> Unit, modifier: Modifier = Modifier, text: String = stringResource(R.string.signature_hint)) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = modifier.padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Text(text, color = MaterialTheme.colorScheme.inverseOnSurface)
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.inversePrimary)
            }
        }
    }
}
