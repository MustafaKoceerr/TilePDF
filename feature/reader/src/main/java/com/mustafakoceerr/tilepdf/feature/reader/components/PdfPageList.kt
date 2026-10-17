package com.mustafakoceerr.tilepdf.feature.reader.components

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mustafakoceerr.tilepdf.core.pdf.PageSize
import com.mustafakoceerr.tilepdf.core.render.PageImageSource
import com.mustafakoceerr.tilepdf.feature.reader.SelectionActions
import com.mustafakoceerr.tilepdf.feature.reader.SelectionUiState
import com.mustafakoceerr.tilepdf.model.Annotation
import com.mustafakoceerr.tilepdf.model.PageTheme
import com.mustafakoceerr.tilepdf.model.ReadingPosition
import com.mustafakoceerr.tilepdf.model.SearchMatch
import kotlinx.coroutines.launch

/** Space between pages and around the list, before zoom. */
internal val PAGE_GAP = 8.dp

/** Zoomable vertical list of all pages. */
@Composable
internal fun PdfPageList(
    pageSizes: List<PageSize>,
    pageImages: PageImageSource,
    initialPosition: ReadingPosition,
    pageTheme: PageTheme,
    matches: List<SearchMatch>,
    currentMatch: SearchMatch?,
    onPositionChanged: (ReadingPosition) -> Unit,
    goToPageOpen: Boolean,
    onGoToPageOpenChange: (Boolean) -> Unit,
    pageJump: PageJump?,
    onPageJumpHandled: () -> Unit,
    onTapPage: (pageIndex: Int, x: Float, y: Float) -> Unit,
    onTapOutsidePage: () -> Unit,
    /** Room for the bars at the top and bottom, kept even while they're hidden. */
    chromePadding: PaddingValues,
    selection: SelectionUiState?,
    selectionActions: SelectionActions,
    modifier: Modifier = Modifier,
    /** TilePDF's annotations by page. */
    annotations: Map<Int, List<Annotation>> = emptyMap(),
) {
    val matchesByPage = remember(matches) { matches.groupBy { it.pageIndex } }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val draftWidthPx = (constraints.maxWidth * DRAFT_SCALE).toInt()
        val density = LocalDensity.current
        val zoomState = rememberZoomState(
            initialScale = initialPosition.zoom,
            initialPanFraction = initialPosition.panFraction,
            initialViewportWidth = constraints.maxWidth,
        )
        val listState = rememberLazyListState(
            initialFirstVisibleItemIndex = initialPosition.pageIndex,
            initialFirstVisibleItemScrollOffset = remember {
                initialScrollOffset(initialPosition, pageSizes, constraints.maxWidth, zoomState.scale, density)
            },
        )

        LaunchedEffect(listState, zoomState) {
            snapshotFlow {
                val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                ReadingPosition(
                    pageIndex = listState.firstVisibleItemIndex,
                    pageOffsetFraction = first?.let {
                        listState.firstVisibleItemScrollOffset.toFloat() / it.size
                    } ?: 0f,
                    zoom = zoomState.scale,
                    panFraction = zoomState.panFraction,
                )
            }.collect(onPositionChanged)
        }

        LaunchedEffect(listState, zoomState, pageImages, pageSizes) {
            requestVisibleTiles(listState, zoomState, pageSizes, pageImages, density)
        }

        TrimMemoryEffect(pageImages) { uiHidden -> pageImages.trimMemory(uiHidden) }

        LaunchedEffect(pageJump) {
            val jump = pageJump ?: return@LaunchedEffect
            val pageIndex = jump.pageIndex.coerceIn(0, pageSizes.lastIndex)
            scrollToPage(listState, zoomState, pageIndex, jump.topPt, pageSizes[pageIndex], density)
            onPageJumpHandled()
        }

        LaunchedEffect(currentMatch) {
            val match = currentMatch ?: return@LaunchedEffect
            scrollToMatch(listState, zoomState, match, pageSizes[match.pageIndex], density)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .zoomable(
                    zoomState = zoomState,
                    listState = listState,
                    onTap = { position ->
                        // The reader clears a selection, follows a link or toggles full screen.
                        pagePointAt(position, listState, zoomState, pageSizes, density)
                            ?.let { (page, x, y) -> onTapPage(page, x, y) }
                            ?: onTapOutsidePage()
                    },
                    onLongPress = { position ->
                        pagePointAt(position, listState, zoomState, pageSizes, density)
                            ?.let { (page, x, y) -> selectionActions.onLongPress(page, x, y) }
                    },
                ),
            // Horizontal padding scales with zoom so the whole content grows
            // proportionally; otherwise page heights would grow by a slightly
            // different factor than the zoom and the scroll anchor would drift.
            contentPadding = PaddingValues(
                start = PAGE_GAP * zoomState.scale,
                end = PAGE_GAP * zoomState.scale,
                top = chromePadding.calculateTopPadding() + PAGE_GAP,
                bottom = chromePadding.calculateBottomPadding() + PAGE_GAP,
            ),
            verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
        ) {
            items(count = pageSizes.size, key = { it }) { pageIndex ->
                PdfPage(
                    pageIndex = pageIndex,
                    pageSize = pageSizes[pageIndex],
                    draftWidthPx = draftWidthPx,
                    pageImages = pageImages,
                    pageTheme = pageTheme,
                    matches = matchesByPage[pageIndex].orEmpty(),
                    currentMatch = currentMatch,
                    selection = selection?.takeIf { it.pageIndex == pageIndex },
                    selectionActions = selectionActions,
                    annotations = annotations[pageIndex].orEmpty(),
                )
            }
        }

        PageNavigationOverlay(
            listState = listState,
            pageCount = pageSizes.size,
            onRequestGoToPage = { onGoToPageOpenChange(true) },
            modifier = Modifier.fillMaxSize().padding(chromePadding),
        )

        if (goToPageOpen) {
            val scope = rememberCoroutineScope()
            GoToPageDialog(
                pageCount = pageSizes.size,
                currentPage = listState.layoutInfo.centerPage() ?: 0,
                onGoToPage = { pageIndex ->
                    onGoToPageOpenChange(false)
                    scope.launch { listState.scrollToItem(pageIndex) }
                },
                onDismiss = { onGoToPageOpenChange(false) },
            )
        }
    }
}

/**
 * Calls [onTrim] when the system asks for memory back, with `uiHidden` true
 * once nothing is being drawn; a new [key] registers again.
 */
@Composable
internal fun TrimMemoryEffect(key: Any, onTrim: (uiHidden: Boolean) -> Unit) {
    val context = LocalContext.current
    val currentOnTrim by rememberUpdatedState(onTrim)
    DisposableEffect(context, key) {
        val callbacks = object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                // Since Android 14 only UI_HIDDEN and BACKGROUND are delivered:
                // drop off-screen caches once the reader isn't visible. Every
                // level from UI_HIDDEN up means nothing is being drawn.
                if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) currentOnTrim(true)
            }

            override fun onConfigurationChanged(newConfig: Configuration) = Unit

            // Still in the foreground: evict, but a frame may still reference
            // what's evicted, so leave freeing it to the garbage collector.
            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = currentOnTrim(false)
        }
        context.registerComponentCallbacks(callbacks)
        onDispose { context.unregisterComponentCallbacks(callbacks) }
    }
}
