package com.mustafakoceerr.tilepdf.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.model.LibraryDocument
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme

private const val PDF_MIME_TYPE = "application/pdf"

@Composable
fun LibraryScreen(
    onOpenDocument: (uri: String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LibraryScreen(
        uiState = uiState,
        onDocumentPicked = { uri ->
            viewModel.onDocumentPicked(uri)
            onOpenDocument(uri)
        },
        onOpenDocument = onOpenDocument,
        onRemove = viewModel::onRemove,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryScreen(
    uiState: LibraryUiState,
    onDocumentPicked: (uri: String) -> Unit,
    onOpenDocument: (uri: String) -> Unit,
    onRemove: (uri: String) -> Unit,
) {
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { onDocumentPicked(it.toString()) } }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.library_title)) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { openDocument.launch(arrayOf(PDF_MIME_TYPE)) },
                icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
                text = { Text(stringResource(R.string.library_open_pdf)) },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                LibraryUiState.Loading -> CircularProgressIndicator()
                is LibraryUiState.Success ->
                    if (uiState.documents.isEmpty()) {
                        Text(
                            text = stringResource(R.string.library_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(32.dp),
                        )
                    } else {
                        DocumentList(uiState.documents, onOpenDocument, onRemove)
                    }
            }
        }
    }
}

@Composable
private fun DocumentList(
    documents: List<LibraryDocument>,
    onOpenDocument: (uri: String) -> Unit,
    onRemove: (uri: String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(documents, key = { it.uri }) { document ->
            ListItem(
                headlineContent = { Text(document.displayName) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.library_page_progress,
                            document.position.pageIndex + 1,
                            document.pageCount,
                        )
                    )
                },
                trailingContent = {
                    IconButton(onClick = { onRemove(document.uri) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.library_remove),
                        )
                    }
                },
                modifier = Modifier.clickable { onOpenDocument(document.uri) },
            )
        }
    }
}

@Preview
@Composable
private fun LibraryScreenPreview() {
    TilePDFTheme {
        LibraryScreen(
            uiState = LibraryUiState.Success(
                listOf(
                    LibraryDocument("content://1", "Kotlin in Action.pdf", 360, ReadingPosition(41), 0),
                    LibraryDocument("content://2", "Clean Architecture.pdf", 432, ReadingPosition(), 0),
                )
            ),
            onDocumentPicked = {},
            onOpenDocument = {},
            onRemove = {},
        )
    }
}

@Preview
@Composable
private fun LibraryScreenEmptyPreview() {
    TilePDFTheme {
        LibraryScreen(LibraryUiState.Success(emptyList()), {}, {}, {})
    }
}
