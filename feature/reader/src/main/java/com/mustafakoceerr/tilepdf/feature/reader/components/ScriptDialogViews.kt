package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.mustafakoceerr.tilepdf.core.pdf.FormScriptHost
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.feature.reader.ScriptAnswer
import com.mustafakoceerr.tilepdf.feature.reader.ScriptDialog

/** A dialog a form script is waiting on; every way out of it answers the script. */
@Composable
internal fun ScriptDialogView(dialog: ScriptDialog, onAnswer: (ScriptAnswer) -> Unit) {
    when (dialog) {
        is ScriptDialog.Alert -> ScriptAlertDialog(dialog, onAnswer)
        is ScriptDialog.Prompt -> ScriptPromptDialog(dialog, onAnswer)
    }
}

@Composable
private fun ScriptAlertDialog(dialog: ScriptDialog.Alert, onAnswer: (ScriptAnswer) -> Unit) {
    fun answer(code: Int) = onAnswer(ScriptAnswer.Button(code))
    val yesNo = dialog.buttons == FormScriptHost.BUTTONS_YES_NO || dialog.buttons == FormScriptHost.BUTTONS_YES_NO_CANCEL
    val cancellable = dialog.buttons == FormScriptHost.BUTTONS_OK_CANCEL || dialog.buttons == FormScriptHost.BUTTONS_YES_NO_CANCEL
    // Dismissing is cancelling where there is a Cancel, otherwise the only safe answer.
    val dismissed = when {
        cancellable -> FormScriptHost.RETURN_CANCEL
        yesNo -> FormScriptHost.RETURN_NO
        else -> FormScriptHost.RETURN_OK
    }
    AlertDialog(
        onDismissRequest = { answer(dismissed) },
        title = { Text(dialog.title.ifBlank { stringResource(R.string.script_title) }) },
        text = { Text(dialog.message) },
        confirmButton = {
            if (yesNo) {
                TextButton(onClick = { answer(FormScriptHost.RETURN_YES) }) { Text(stringResource(R.string.script_yes)) }
            } else {
                TextButton(onClick = { answer(FormScriptHost.RETURN_OK) }) { Text(stringResource(R.string.script_ok)) }
            }
        },
        dismissButton = {
            if (yesNo) {
                TextButton(onClick = { answer(FormScriptHost.RETURN_NO) }) { Text(stringResource(R.string.script_no)) }
            }
            if (cancellable) {
                TextButton(onClick = { answer(FormScriptHost.RETURN_CANCEL) }) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun ScriptPromptDialog(dialog: ScriptDialog.Prompt, onAnswer: (ScriptAnswer) -> Unit) {
    var text by rememberSaveable { mutableStateOf(dialog.default) }
    AlertDialog(
        onDismissRequest = { onAnswer(ScriptAnswer.Text(null)) },
        title = { Text(dialog.title.ifBlank { stringResource(R.string.script_title) }) },
        text = {
            Column {
                Text(dialog.question)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = dialog.label.takeIf { it.isNotBlank() }?.let { label -> { Text(label) } },
                    singleLine = true,
                    visualTransformation = if (dialog.password) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = if (dialog.password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAnswer(ScriptAnswer.Text(text)) }) { Text(stringResource(R.string.script_ok)) }
        },
        dismissButton = {
            TextButton(onClick = { onAnswer(ScriptAnswer.Text(null)) }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
