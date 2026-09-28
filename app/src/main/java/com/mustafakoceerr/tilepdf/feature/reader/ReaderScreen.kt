package com.mustafakoceerr.tilepdf.feature.reader

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.feature.reader.components.PdfPageList
import com.mustafakoceerr.tilepdf.feature.reader.components.SearchTopBar
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme

@Composable
fun ReaderScreen(
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ReaderScreen(
        uiState = uiState,
        onBack = onBack,
        onToggleNightMode = viewModel::onToggleNightMode,
        onPositionChanged = viewModel::onPositionChanged,
        searchActions = SearchActions(
            onOpen = viewModel::onOpenSearch,
            onQueryChange = viewModel::onSearchQueryChange,
            onPrevious = viewModel::onPreviousMatch,
            onNext = viewModel::onNextMatch,
            onClose = viewModel::onCloseSearch,
        ),
    )
}

/** Search events, grouped to keep the screen's signature readable. */
internal class SearchActions(
    val onOpen: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onClose: () -> Unit,
) {
    companion object {
        val None = SearchActions({}, {}, {}, {}, {})
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(
    uiState: ReaderUiState,
    onBack: () -> Unit,
    onToggleNightMode: () -> Unit,
    onPositionChanged: (ReadingPosition) -> Unit,
    searchActions: SearchActions,
) {
    val ready = uiState as? ReaderUiState.Ready
    val search = ready?.search
    var goToPageOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = search?.isActive == true, onBack = searchActions.onClose)

    Scaffold(
        topBar = {
            if (search?.isActive == true) {
                SearchTopBar(
                    state = search,
                    onQueryChange = searchActions.onQueryChange,
                    onPrevious = searchActions.onPrevious,
                    onNext = searchActions.onNext,
                    onClose = searchActions.onClose,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = ready?.title ?: stringResource(R.string.app_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back),
                            )
                        }
                    },
                    actions = {
                        if (ready != null) {
                            IconButton(onClick = { goToPageOpen = true }) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_tag),
                                    contentDescription = stringResource(R.string.reader_go_to_page),
                                )
                            }
                            IconButton(onClick = searchActions.onOpen) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_search),
                                    contentDescription = stringResource(R.string.reader_search),
                                )
                            }
                            IconToggleButton(
                                checked = ready.nightMode,
                                onCheckedChange = { onToggleNightMode() },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_dark_mode),
                                    contentDescription = stringResource(R.string.reader_night_mode),
                                )
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                // Edge-to-edge: the keyboard overlaps unless we shrink for it,
                // and search would scroll matches behind the keyboard.
                .imePadding(),
            contentAlignment = Alignment.Center,
        ) {
            when (uiState) {
                ReaderUiState.Loading -> CircularProgressIndicator()
                is ReaderUiState.Error -> Text(
                    text = stringResource(uiState.reason.messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(24.dp),
                )
                is ReaderUiState.Ready -> PdfPageList(
                    pageSizes = uiState.pageSizes,
                    pageImages = uiState.pageImages,
                    initialPosition = uiState.initialPosition,
                    nightMode = uiState.nightMode,
                    matches = uiState.search.matches,
                    currentMatch = uiState.search.currentMatch,
                    onPositionChanged = onPositionChanged,
                    goToPageOpen = goToPageOpen,
                    onGoToPageOpenChange = { goToPageOpen = it },
                )
            }
        }
    }
}

@get:StringRes
private val ReaderError.messageRes: Int
    get() = when (this) {
        ReaderError.NotFound -> R.string.reader_error_not_found
        ReaderError.PasswordProtected -> R.string.reader_error_password
        ReaderError.Corrupt -> R.string.reader_error_corrupt
    }

@Preview
@Composable
private fun ReaderScreenErrorPreview() {
    TilePDFTheme {
        ReaderScreen(ReaderUiState.Error(ReaderError.PasswordProtected), {}, {}, {}, SearchActions.None)
    }
}
