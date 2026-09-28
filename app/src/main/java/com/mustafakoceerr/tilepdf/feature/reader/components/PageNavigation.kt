package com.mustafakoceerr.tilepdf.feature.reader.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long the scrollbar and page indicator stay after scrolling stops. */
private const val HIDE_DELAY_MS = 1_500L

private val ThumbTouchWidth = 48.dp
private val ThumbHeight = 56.dp
private val ThumbVisibleWidth = 8.dp

/**
 * The page under the middle of the viewport: what the reader is looking at,
 * even when the previous page still shows a sliver at the top.
 */
internal fun LazyListLayoutInfo.centerPage(): Int? {
    // Item offsets and these bounds share one space (0 = end of top padding).
    val center = (viewportStartOffset + viewportEndOffset) / 2
    return visibleItemsInfo.firstOrNull { it.offset <= center && center < it.offset + it.size }?.index
        ?: visibleItemsInfo.firstOrNull()?.index
}

/**
 * How far through the document the list is scrolled, `0..1`. Pages can
 * differ in height and only visible ones are measured, so the total is
 * estimated from the average visible item; the ends are exact.
 */
internal fun LazyListState.scrollProgress(itemCount: Int): Float {
    if (!canScrollBackward) return 0f
    if (!canScrollForward) return 1f
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty() || itemCount == 0) return 0f
    val averageItem = visible.sumOf { it.size }.toFloat() / visible.size + info.mainAxisItemSpacing
    val scrolled = firstVisibleItemIndex * averageItem + firstVisibleItemScrollOffset
    val scrollable = averageItem * itemCount - info.viewportSize.height
    return (scrolled / scrollable).coerceIn(0f, 1f)
}

/** Page for a scrollbar position `0..1`. */
internal fun pageForProgress(progress: Float, pageCount: Int): Int =
    (progress.coerceIn(0f, 1f) * (pageCount - 1)).roundToInt()

/** 1-based page number typed by the user, as a 0-based index, or `null` if invalid. */
internal fun parsePageNumber(text: String, pageCount: Int): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..pageCount }?.minus(1)

/**
 * Overlays for moving through a long document: a draggable scrollbar thumb
 * and a "12 / 300" page indicator, both shown while scrolling; tapping the
 * indicator asks for a page number.
 */
@Composable
internal fun PageNavigationOverlay(
    listState: LazyListState,
    pageCount: Int,
    onRequestGoToPage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val active = listState.isScrollInProgress || dragging
    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }
    val currentPage = listState.layoutInfo.centerPage() ?: 0

    Box(modifier) {
        if (pageCount > 1) {
            FastScroller(
                listState = listState,
                pageCount = pageCount,
                visible = visible,
                onDraggingChange = { dragging = it },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
            )
        }
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        ) {
            PageIndicator(currentPage + 1, pageCount, onClick = onRequestGoToPage)
        }
    }
}

@Composable
private fun PageIndicator(pageNumber: Int, pageCount: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 2.dp,
    ) {
        Text(
            text = stringResource(R.string.reader_page_indicator, pageNumber, pageCount),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/**
 * Scrollbar whose thumb can be dragged to jump through the document. Only the
 * thumb takes touches, so the rest of the right edge still scrolls the list.
 */
@Composable
private fun FastScroller(
    listState: LazyListState,
    pageCount: Int,
    visible: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // While dragging, the thumb follows the finger rather than the list, whose
    // estimated progress would make it jitter.
    var dragProgress by remember { mutableFloatStateOf(-1f) }
    val progress = if (dragProgress >= 0f) dragProgress else listState.scrollProgress(pageCount)
    val description = stringResource(R.string.reader_fast_scroll)

    BoxWithConstraints(modifier.padding(vertical = 8.dp)) {
        val density = LocalDensity.current
        val thumbHeightPx = with(density) { ThumbHeight.toPx() }
        val travelPx = (constraints.maxHeight - thumbHeightPx).coerceAtLeast(1f)

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.offset { IntOffset(0, (progress * travelPx).roundToInt()) },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(ThumbTouchWidth, ThumbHeight)
                    .semantics { contentDescription = description }
                    .pointerInput(pageCount, travelPx) {
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragProgress = listState.scrollProgress(pageCount)
                                onDraggingChange(true)
                            },
                            onDragEnd = {
                                dragProgress = -1f
                                onDraggingChange(false)
                            },
                            onDragCancel = {
                                dragProgress = -1f
                                onDraggingChange(false)
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            dragProgress = (dragProgress + dragAmount / travelPx).coerceIn(0f, 1f)
                            // Applied at the next layout; no coroutine per move.
                            listState.requestScrollToItem(pageForProgress(dragProgress, pageCount))
                        }
                    },
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(ThumbVisibleWidth, ThumbHeight),
                ) {}
            }
        }
    }
}

@Composable
internal fun GoToPageDialog(
    pageCount: Int,
    currentPage: Int,
    onGoToPage: (pageIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val pageIndex = parsePageNumber(text, pageCount)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_go_to_page)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter(Char::isDigit).take(6) },
                placeholder = { Text((currentPage + 1).toString()) },
                supportingText = { Text(stringResource(R.string.reader_go_to_page_range, pageCount)) },
                isError = text.isNotEmpty() && pageIndex == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { pageIndex?.let(onGoToPage) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { pageIndex?.let(onGoToPage) }, enabled = pageIndex != null) {
                Text(stringResource(R.string.reader_go_to_page_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
