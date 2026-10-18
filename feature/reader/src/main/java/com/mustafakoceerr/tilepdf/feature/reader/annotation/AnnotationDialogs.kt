package com.mustafakoceerr.tilepdf.feature.reader.annotation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.model.MarkupKind
import com.mustafakoceerr.tilepdf.model.Note

internal const val NOTE_FIELD_TAG = "noteField"

/** Highlight, underline or strike out the selected text. */
@Composable
internal fun SelectionMarkupBar(enabled: Boolean, onMarkup: (MarkupKind) -> Unit, modifier: Modifier = Modifier) {
    Surface(tonalElevation = 3.dp, shadowElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            MarkupKind.entries.forEach { kind ->
                FilledTonalButton(onClick = { onMarkup(kind) }, enabled = enabled) {
                    Text(stringResource(kind.actionRes))
                }
            }
        }
    }
}

/** What a tapped annotation is, with Delete and, for TilePDF's notes, Edit. */
@Composable
internal fun AnnotationMenuDialog(
    target: AnnotationTarget,
    onDelete: () -> Unit,
    onEditNote: (Note) -> Unit,
    onDismiss: () -> Unit,
) {
    val item = annotationListItems(
        own = listOfNotNull((target as? AnnotationTarget.Own)?.annotation),
        foreign = listOfNotNull((target as? AnnotationTarget.Foreign)?.annotation),
    ).single()
    val note = ((target as? AnnotationTarget.Own)?.annotation as? Note)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(item.kind.labelRes)) },
        text = {
            Text(
                text = listOf(stringResource(R.string.annotation_page, item.pageIndex + 1), item.text)
                    .filter { it.isNotBlank() }.joinToString("\n"),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            if (note != null) {
                TextButton(onClick = { onEditNote(note) }) { Text(stringResource(R.string.annotation_edit)) }
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.annotation_delete)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.reader_close)) } },
    )
}

/** Writes a new note or edits one; blank text adds nothing, or deletes the note being edited. */
@Composable
internal fun NoteDialog(
    initial: String,
    editing: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.annotation_note_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.annotation_note_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                minLines = 3,
                modifier = Modifier.fillMaxWidth().testTag(NOTE_FIELD_TAG),
            )
        },
        confirmButton = {
            if (editing) TextButton(onClick = onDelete) { Text(stringResource(R.string.annotation_delete)) }
            TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.annotation_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@get:StringRes
internal val MarkupKind.actionRes: Int
    get() = when (this) {
        MarkupKind.Highlight -> R.string.annotation_highlight
        MarkupKind.Underline -> R.string.annotation_underline
        MarkupKind.StrikeOut -> R.string.annotation_strikeout
    }

@get:StringRes
internal val AnnotationKind.labelRes: Int
    get() = when (this) {
        AnnotationKind.Highlight -> R.string.annotation_kind_highlight
        AnnotationKind.Underline -> R.string.annotation_kind_underline
        AnnotationKind.StrikeOut -> R.string.annotation_kind_strikeout
        AnnotationKind.Ink -> R.string.annotation_kind_ink
        AnnotationKind.Note -> R.string.annotation_kind_note
        AnnotationKind.Other -> R.string.annotation_kind_other
    }
