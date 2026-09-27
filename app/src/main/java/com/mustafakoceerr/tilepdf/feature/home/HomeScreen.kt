package com.mustafakoceerr.tilepdf.feature.home

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.mustafakoceerr.tilepdf.R

private const val PDF_MIME_TYPE = "application/pdf"

@Composable
fun HomeScreen(onPdfSelected: (uri: String) -> Unit) {
    val contentResolver = LocalContext.current.contentResolver
    val openDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Persist access so the file can be reopened later from the library.
        // Some providers don't offer persistable grants; the one-off grant is
        // still enough for this session.
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
        }
        onPdfSelected(uri.toString())
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = { openDocument.launch(arrayOf(PDF_MIME_TYPE)) }) {
                Text(stringResource(R.string.home_open_pdf))
            }
        }
    }
}
