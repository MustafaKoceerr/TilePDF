package com.mustafakoceerr.tilepdf.testing

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.ui.unit.IntSize

/** A vertical list item; its key is its index, as in the reader's page list. */
data class FakeListItem(
    override val index: Int,
    override val offset: Int,
    override val size: Int,
) : LazyListItemInfo {
    override val key: Any get() = index
    override val contentType: Any? get() = null
}

/**
 * Layout info of a vertical list, following LazyColumn's convention that
 * item offsets start after the top content padding.
 */
class FakeLazyListLayoutInfo(
    override val visibleItemsInfo: List<LazyListItemInfo>,
    override val viewportSize: IntSize = IntSize(1000, 2000),
    override val beforeContentPadding: Int = 0,
    override val afterContentPadding: Int = 0,
    override val mainAxisItemSpacing: Int = 0,
    override val totalItemsCount: Int = 100,
) : LazyListLayoutInfo {
    override val viewportStartOffset: Int get() = -beforeContentPadding
    override val viewportEndOffset: Int get() = viewportSize.height - beforeContentPadding
    override val orientation: Orientation get() = Orientation.Vertical
    override val reverseLayout: Boolean get() = false
}
