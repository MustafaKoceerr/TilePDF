package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mustafakoceerr.tilepdf.feature.reader.R

/** Shows where a link leads before another app opens it. */
@Composable
internal fun LinkConfirmDialog(link: String, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.link_confirm_title)) },
        text = { Text(link) },
        confirmButton = { TextButton(onClick = onOpen) { Text(stringResource(R.string.reader_password_open)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
