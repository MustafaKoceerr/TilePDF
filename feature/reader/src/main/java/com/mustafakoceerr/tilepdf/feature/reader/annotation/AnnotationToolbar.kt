package com.mustafakoceerr.tilepdf.feature.reader.annotation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.feature.reader.R

/** Colours offered for markup, notes and the pen. */
internal val AnnotationPalette = listOf(
    0xFFFFEB3B, 0xFF66BB6A, 0xFF42A5F5, 0xFFEC407A, 0xFFE53935, 0xFF212121,
).map { it.toInt() }

/** Pen widths in points. */
internal val PenWidths = listOf(1f, 2f, 4f)

/** What the annotation toolbar shows. */
internal data class ToolbarState(
    val tool: AnnotationTool,
    val markupColor: Int,
    val penColor: Int,
    val penWidthPt: Float,
    val canUndo: Boolean,
    val canRedo: Boolean,
)

/** Tools, undo/redo and Done; below them the colours and, for the pen, widths. */
@Composable
internal fun AnnotationToolbar(
    state: ToolbarState,
    onTool: (AnnotationTool) -> Unit,
    onMarkupColor: (Int) -> Unit,
    onPenColor: (Int) -> Unit,
    onPenWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnnotationTool.entries.forEach { tool ->
                    IconToggleButton(checked = state.tool == tool, onCheckedChange = { onTool(tool) }) {
                        Icon(painterResource(tool.icon), contentDescription = stringResource(tool.label))
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onUndo, enabled = state.canUndo) {
                    Icon(painterResource(TilePdfIcons.Undo), contentDescription = stringResource(R.string.annotation_undo))
                }
                IconButton(onClick = onRedo, enabled = state.canRedo) {
                    Icon(painterResource(TilePdfIcons.Redo), contentDescription = stringResource(R.string.annotation_redo))
                }
                TextButton(onClick = onDone) { Text(stringResource(R.string.reader_annotate_done)) }
            }
            when (state.tool) {
                AnnotationTool.Pen -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Swatches(state.penColor, onPenColor)
                    Spacer(Modifier.width(12.dp))
                    PenWidths.forEach { width -> WidthChoice(width, selected = width == state.penWidthPt, onClick = { onPenWidth(width) }) }
                }
                AnnotationTool.Select, AnnotationTool.Note -> Swatches(state.markupColor, onMarkupColor)
                AnnotationTool.Eraser -> Unit
            }
        }
    }
}

@Composable
private fun Swatches(selected: Int, onSelect: (Int) -> Unit) {
    val description = stringResource(R.string.annotation_color)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        AnnotationPalette.forEach { color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .selectable(selected = color == selected, role = Role.RadioButton, onClick = { onSelect(color) })
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(Color(color), CircleShape)
                        .border(
                            width = if (color == selected) 3.dp else 1.dp,
                            color = if (color == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

/** A width shown as a line that thick. */
@Composable
private fun WidthChoice(widthPt: Float, selected: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.annotation_width)
    Box(
        modifier = Modifier
            .size(40.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$description ${widthPt.toInt()}" }
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.width(20.dp).height((widthPt * 1.5f).dp).background(MaterialTheme.colorScheme.onSurface, CircleShape))
    }
}

@get:DrawableRes
private val AnnotationTool.icon: Int
    get() = when (this) {
        AnnotationTool.Select -> TilePdfIcons.TextSelect
        AnnotationTool.Pen -> TilePdfIcons.Edit
        AnnotationTool.Eraser -> TilePdfIcons.InkEraser
        AnnotationTool.Note -> TilePdfIcons.StickyNote
    }

@get:StringRes
private val AnnotationTool.label: Int
    get() = when (this) {
        AnnotationTool.Select -> R.string.annotation_tool_select
        AnnotationTool.Pen -> R.string.annotation_tool_pen
        AnnotationTool.Eraser -> R.string.annotation_tool_eraser
        AnnotationTool.Note -> R.string.annotation_tool_note
    }
