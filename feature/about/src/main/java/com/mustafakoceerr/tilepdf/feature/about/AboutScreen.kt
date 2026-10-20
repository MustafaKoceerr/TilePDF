package com.mustafakoceerr.tilepdf.feature.about

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mustafakoceerr.tilepdf.core.designsystem.TilePdfIcons
import com.mustafakoceerr.tilepdf.core.designsystem.readableWidth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The version, the offline promise, and every third-party license: PDFium's
 * own (from core:pdf's assets) and the Maven libraries' (from [librariesRes],
 * AboutLibraries' generated list in the app module).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit, @RawRes librariesRes: Int) {
    var showingLibraries by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showingLibraries) { showingLibraries = false }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (showingLibraries) R.string.about_libraries else R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (showingLibraries) showingLibraries = false else onBack() }) {
                        Icon(painterResource(TilePdfIcons.ArrowBack), contentDescription = stringResource(R.string.about_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        if (showingLibraries) {
            val libraries by produceLibraries(librariesRes)
            LibrariesContainer(libraries, Modifier.fillMaxSize().padding(innerPadding))
        } else {
            AboutContent(onOpenLibraries = { showingLibraries = true }, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun AboutContent(onOpenLibraries: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val version = remember(context) { context.versionName() }
    val files = remember(context) {
        pdfiumLicenseFiles(context.assets.list(PDFIUM_LICENSES_DIR)?.toList().orEmpty())
    }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    // The whole screen scrolls; each row keeps to a readable width in the middle.
    LazyColumn(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            Column(Modifier.readableWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("TilePDF", style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(R.string.about_offline),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                HorizontalDivider()
            }
        }
        item { SectionTitle(R.string.about_pdfium) }
        items(files, key = { it }) { file ->
            ListItem(
                headlineContent = { Text(componentName(file)) },
                modifier = Modifier.readableWidth().clickable { open = file },
            )
        }
        item {
            Column(Modifier.readableWidth()) {
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.about_libraries)) },
                    supportingContent = { Text(stringResource(R.string.about_libraries_summary)) },
                    modifier = Modifier.clickable(onClick = onOpenLibraries),
                )
            }
        }
    }

    open?.let { file -> LicenseDialog(file, onDismiss = { open = null }) }
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .readableWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
            .semantics { heading() },
    )
}

/** One license text, read off the main thread. */
@Composable
private fun LicenseDialog(file: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text by produceState("", file) {
        value = withContext(Dispatchers.IO) {
            context.assets.open("$PDFIUM_LICENSES_DIR/$file").bufferedReader().use { it.readText() }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(componentName(file)) },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) } },
    )
}

private fun Context.versionName(): String =
    packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
