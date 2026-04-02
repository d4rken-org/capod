package eu.darken.capod.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class ReorderableState<T>(
    val lazyListState: LazyListState,
    private val hapticFeedback: HapticFeedback,
    private val key: (T) -> Any,
) {
    private val _items = mutableStateListOf<T>()
    val items: List<T> get() = _items

    private var originalItems: List<T> = emptyList()

    var draggedIndex: Int? by mutableStateOf(null)
        private set

    var dragOffsetY: Float by mutableFloatStateOf(0f)
        private set

    val isDragging: Boolean get() = draggedIndex != null

    fun syncItems(newItems: List<T>) {
        if (isDragging) return
        _items.clear()
        _items.addAll(newItems)
    }

    fun onDragStart(item: T) {
        val itemKey = key(item)
        val index = _items.indexOfFirst { key(it) == itemKey }
        if (index == -1) return
        originalItems = _items.toList()
        draggedIndex = index
        dragOffsetY = 0f
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun onDrag(delta: Float) {
        val currentIndex = draggedIndex ?: return
        dragOffsetY += delta

        val layoutInfo = lazyListState.layoutInfo
        val draggedItemInfo = layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentIndex } ?: return
        val draggedCenter = draggedItemInfo.offset + draggedItemInfo.size / 2 + dragOffsetY.toInt()

        val targetInfo = layoutInfo.visibleItemsInfo.firstOrNull { itemInfo ->
            itemInfo.index != currentIndex &&
                itemInfo.index < _items.size &&
                draggedCenter in itemInfo.offset..(itemInfo.offset + itemInfo.size)
        } ?: return

        val targetIndex = targetInfo.index
        dragOffsetY += (draggedItemInfo.offset - targetInfo.offset).toFloat()
        _items.swap(currentIndex, targetIndex)
        draggedIndex = targetIndex
    }

    fun onDragEnd(onReorder: (List<T>) -> Unit) {
        val reordered = _items.toList()
        draggedIndex = null
        dragOffsetY = 0f
        originalItems = emptyList()
        onReorder(reordered)
    }

    fun onDragCancel() {
        if (originalItems.isNotEmpty()) {
            _items.clear()
            _items.addAll(originalItems)
        }
        draggedIndex = null
        dragOffsetY = 0f
        originalItems = emptyList()
    }
}

private fun <T> MutableList<T>.swap(from: Int, to: Int) {
    val temp = this[from]
    this[from] = this[to]
    this[to] = temp
}

@Composable
fun <T> rememberReorderableState(
    lazyListState: LazyListState,
    items: List<T>,
    key: (T) -> Any,
): ReorderableState<T> {
    val hapticFeedback = LocalHapticFeedback.current
    return remember(lazyListState) {
        ReorderableState(lazyListState, hapticFeedback, key).also {
            it.syncItems(items)
        }
    }
}

@Composable
fun <T> ReorderableAutoScroll(state: ReorderableState<T>) {
    val scrollThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }
    LaunchedEffect(state.isDragging) {
        if (!state.isDragging) return@LaunchedEffect
        while (isActive) {
            val layoutInfo = state.lazyListState.layoutInfo
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            val draggedIdx = state.draggedIndex ?: break
            val draggedItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIdx }
            if (draggedItem != null) {
                val itemCenter = draggedItem.offset + draggedItem.size / 2 + state.dragOffsetY
                val scrollSpeed = 8f
                val delta = when {
                    itemCenter < viewportStart + scrollThresholdPx -> -scrollSpeed
                    itemCenter > viewportEnd - scrollThresholdPx -> scrollSpeed
                    else -> 0f
                }
                if (delta != 0f) state.lazyListState.scrollBy(delta)
            }
            delay(16)
        }
    }
}

@Composable
fun <T> reorderableItemModifier(
    state: ReorderableState<T>,
    index: Int,
    item: T,
    enabled: Boolean = true,
    onReorder: (List<T>) -> Unit = {},
): Modifier {
    val isDragging = state.draggedIndex == index
    val dragOffsetY = if (isDragging) state.dragOffsetY else 0f

    val currentItem by rememberUpdatedState(item)
    val currentOnReorder by rememberUpdatedState(onReorder)

    val surfaceColor = MaterialTheme.colorScheme.surface

    return Modifier
        .zIndex(if (isDragging) 1f else 0f)
        .background(surfaceColor)
        .then(
            if (enabled) {
                Modifier.pointerInput(Unit) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { state.onDragStart(currentItem) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.onDrag(dragAmount.y)
                        },
                        onDragEnd = { state.onDragEnd(currentOnReorder) },
                        onDragCancel = { state.onDragCancel() },
                    )
                }
            } else {
                Modifier
            }
        )
        .graphicsLayer {
            if (isDragging) {
                translationY = dragOffsetY
                shadowElevation = 8.dp.toPx()
            }
        }
}
