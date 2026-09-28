package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.R
import com.mustafakoceerr.tilepdf.feature.reader.SearchUiState
import com.mustafakoceerr.tilepdf.ui.theme.TilePDFTheme

/** Find-in-document bar that replaces the reader's top app bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopBar(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.reader_search_close),
                )
            }
        },
        title = {
            TextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.reader_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        actions = {
            SearchProgress(state)
            IconButton(onClick = onPrevious, enabled = state.matches.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_up),
                    contentDescription = stringResource(R.string.reader_search_previous),
                )
            }
            IconButton(onClick = onNext, enabled = state.matches.isNotEmpty()) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = stringResource(R.string.reader_search_next),
                )
            }
        },
    )
}

@Composable
private fun SearchProgress(state: SearchUiState) {
    when {
        state.matches.isNotEmpty() -> Text(
            text = stringResource(
                R.string.reader_search_counter,
                state.currentIndex + 1,
                state.matches.size,
            ),
            style = MaterialTheme.typography.labelLarge,
        )
        state.isSearching -> CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        state.query.isNotBlank() -> Text(
            text = stringResource(R.string.reader_search_no_results),
            style = MaterialTheme.typography.labelLarge,
        )
    }
    // Growing results keep a spinner next to the counter.
    if (state.isSearching && state.matches.isNotEmpty()) {
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(16.dp),
        )
    }
}

@Preview
@Composable
private fun SearchTopBarPreview() {
    TilePDFTheme {
        SearchTopBar(
            state = SearchUiState(isActive = true, query = "tile", currentIndex = 2),
            onQueryChange = {},
            onPrevious = {},
            onNext = {},
            onClose = {},
        )
    }
}
