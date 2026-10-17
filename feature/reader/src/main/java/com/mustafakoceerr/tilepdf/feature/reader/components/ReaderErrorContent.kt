package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.feature.reader.ErrorAction
import com.mustafakoceerr.tilepdf.feature.reader.R
import com.mustafakoceerr.tilepdf.feature.reader.ReaderError
import com.mustafakoceerr.tilepdf.feature.reader.errorCopy
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme

private const val PDF_MIME_TYPE = "application/pdf"

/**
 * Why [error] kept the document from opening, with the next step: choosing
 * the file again, retrying, or going back to the library.
 */
@Composable
internal fun ReaderErrorContent(
    error: ReaderError,
    onFileChosen: (uri: String) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chooseFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onFileChosen(it.toString()) }
    }
    val copy = error.errorCopy()
    val run: (ErrorAction) -> Unit = { action ->
        when (action) {
            ErrorAction.ChooseFile -> chooseFile.launch(arrayOf(PDF_MIME_TYPE))
            ErrorAction.Retry -> onRetry()
            ErrorAction.Back -> onBack()
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
    ) {
        Icon(
            painter = painterResource(TilePdfIcons.ErrorOutline),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = stringResource(copy.title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(copy.message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Button(onClick = { run(copy.primary) }) { Text(stringResource(copy.primary.labelRes)) }
        copy.secondary?.let { secondary ->
            TextButton(onClick = { run(secondary) }) { Text(stringResource(secondary.labelRes)) }
        }
    }
}

private val ErrorAction.labelRes: Int
    get() = when (this) {
        ErrorAction.ChooseFile -> R.string.error_choose_file
        ErrorAction.Retry -> R.string.error_try_again
        ErrorAction.Back -> R.string.error_back_to_library
    }

@Preview
@Composable
private fun ReaderErrorContentPreview() {
    TilePDFTheme {
        ReaderErrorContent(ReaderError.PermissionLost, onFileChosen = {}, onRetry = {}, onBack = {})
    }
}
