package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.feature.reader.RecognitionProgress

/** Asks which pages to recognise text on. */
@Composable
internal fun RecognizeTextDialog(onRecognize: (allPages: Boolean) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(TilePdfIcons.TextScan), contentDescription = null) },
        title = { Text(stringResource(R.string.recognize_title)) },
        text = { Text(stringResource(R.string.recognize_explanation)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onRecognize(false) }) { Text(stringResource(R.string.recognize_this_page)) }
                TextButton(onClick = { onRecognize(true) }) { Text(stringResource(R.string.recognize_all_pages)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

/** Shown while text is being recognised, with a way to stop. */
@Composable
internal fun RecognitionProgressBar(progress: RecognitionProgress, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = modifier.padding(16.dp).widthIn(max = 480.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.recognize_progress, progress.done, progress.total),
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
            LinearProgressIndicator(
                progress = { if (progress.total == 0) 0f else progress.done.toFloat() / progress.total },
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            )
        }
    }
}

/** Offers recognition on a page that has no text, as scanned pages don't. */
@Composable
internal fun NoTextChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ElevatedAssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.recognize_no_text_chip)) },
        leadingIcon = { Icon(painterResource(TilePdfIcons.TextScan), contentDescription = null) },
        modifier = modifier.padding(bottom = 16.dp),
    )
}
