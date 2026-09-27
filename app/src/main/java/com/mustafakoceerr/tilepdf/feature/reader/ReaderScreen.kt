package com.mustafakoceerr.tilepdf.feature.reader

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.core.pdf.PageSize

/** Drafts are rendered at a fraction of the screen width to keep them cheap. */
private const val DRAFT_SCALE = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = uiState) {
                ReaderUiState.Loading -> CircularProgressIndicator()
                is ReaderUiState.Error -> Text(
                    text = stringResource(state.reason.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
                is ReaderUiState.Ready -> PageList(
                    pageSizes = state.pageSizes,
                    cachedDraft = viewModel::cachedDraft,
                    loadDraft = viewModel::draft,
                )
            }
        }
    }
}

@Composable
private fun PageList(
    pageSizes: List<PageSize>,
    cachedDraft: (pageIndex: Int, widthPx: Int) -> Bitmap?,
    loadDraft: suspend (pageIndex: Int, widthPx: Int) -> Bitmap?,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val draftWidthPx = (constraints.maxWidth * DRAFT_SCALE).toInt()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(count = pageSizes.size, key = { it }) { pageIndex ->
                DraftPage(
                    pageIndex = pageIndex,
                    pageSize = pageSizes[pageIndex],
                    draftWidthPx = draftWidthPx,
                    cachedDraft = cachedDraft,
                    loadDraft = loadDraft,
                )
            }
        }
    }
}

@Composable
private fun DraftPage(
    pageIndex: Int,
    pageSize: PageSize,
    draftWidthPx: Int,
    cachedDraft: (pageIndex: Int, widthPx: Int) -> Bitmap?,
    loadDraft: suspend (pageIndex: Int, widthPx: Int) -> Bitmap?,
) {
    // Seed from the cache so pages scrolled back into view don't flash blank.
    var bitmap by remember(pageIndex, draftWidthPx) {
        mutableStateOf(cachedDraft(pageIndex, draftWidthPx))
    }
    // Cancelled automatically when the item leaves the composition.
    LaunchedEffect(pageIndex, draftWidthPx) {
        if (bitmap == null) bitmap = loadDraft(pageIndex, draftWidthPx)
    }

    // Sized from the page's aspect ratio up front so the layout never jumps.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(pageSize.aspectRatio)
            .background(Color.White),
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.reader_page_description, pageIndex + 1),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val ReaderError.messageRes: Int
    get() = when (this) {
        ReaderError.NotFound -> R.string.reader_error_not_found
        ReaderError.PasswordProtected -> R.string.reader_error_password
        ReaderError.Corrupt -> R.string.reader_error_corrupt
    }
