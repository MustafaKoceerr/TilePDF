package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.model.FormField
import com.mustafakoceerr.tilepdf.model.FormFieldKind

private const val MULTILINE_MIN_LINES = 3

/** Fills in a text field, or picks a combo or list box option. */
@Composable
internal fun FormFieldDialog(field: FormField, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    when (field.kind) {
        FormFieldKind.Text -> FormTextDialog(field, onSubmit, onDismiss)
        FormFieldKind.ComboBox, FormFieldKind.ListBox -> FormChoiceDialog(field, onSubmit, onDismiss)
        FormFieldKind.CheckBox, FormFieldKind.RadioButton -> Unit
    }
}

@Composable
private fun FormTextDialog(field: FormField, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(field.name, stateSaver = TextFieldValue.Saver) {
        // The cursor at the end, ready to go on typing.
        mutableStateOf(TextFieldValue(field.value, TextRange(field.value.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.name.ifBlank { stringResource(R.string.form_text_field) }) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = !field.multiline,
                minLines = if (field.multiline) MULTILINE_MIN_LINES else 1,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(text.text) }) { Text(stringResource(R.string.annotation_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Choosing an option fills the field at once, like a menu. */
@Composable
private fun FormChoiceDialog(field: FormField, onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(field.name.ifBlank { stringResource(R.string.form_text_field) }) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                field.options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == field.value, role = Role.RadioButton, onClick = { onSubmit(option) })
                            .padding(vertical = 8.dp),
                    ) {
                        RadioButton(selected = option == field.value, onClick = null)
                        Text(option, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
