package net.bible.sharedui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * Generic drag-reorder list. Wraps a battle-tested CMP reorderable engine (edge auto-scroll,
 * animated displacement, key tracking) behind a stable, impl-swappable API. The row applies
 * [content]'s `dragHandleModifier` to its drag affordance. Foundational — reused by Batch 8's
 * workspace selector.
 *
 * [initialScrollKey]: when its item exists, the list scrolls once so that item is visible (no scroll
 * if it already is). Animated unless "Disable animations" is on, then it jumps.
 */
@Composable
fun <T> AbReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onMove: (from: Int, to: Int) -> Unit,
    modifier: Modifier = Modifier,
    longPressToDrag: Boolean = false,
    initialScrollKey: Any? = null,
    content: @Composable (item: T, dragHandleModifier: Modifier) -> Unit,
) {
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        onMove(from.index, to.index)
    }
    val targetIndex = initialScrollKey?.let { k -> items.indexOfFirst { key(it) == k } } ?: -1
    ScrollToItemOnce(listState, targetIndex)
    LazyColumn(state = listState, modifier = modifier.fillMaxWidth().volumeScrollTarget(listState)) {
        items(items, key = { key(it) }) { item ->
            ReorderableItem(reorderState, key = key(item)) { _ ->
                val handle = if (longPressToDrag) Modifier.longPressDraggableHandle()
                             else Modifier.draggableHandle()
                content(item, handle)
            }
        }
    }
}
