package com.ggym.app.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A short list whose rows can be reordered by dragging a handle.
 *
 * The screens that use it (a routine's days, a day's exercises) are 3–15 rows long, so this is a
 * `Column` in a scroll, not a `LazyColumn`: every row is measured, which is exactly what the drag
 * math needs, and there is no off-screen item that a drag could silently pass.
 *
 * The interaction is deliberately a **handle**, not a long press on the row: the row is also a
 * button (it opens the day/exercise editor), and a long press that sometimes reorders and sometimes
 * opens is a coin toss. Tapping the handle does nothing; dragging it moves the row. For a screen
 * reader the handle carries "Move up" / "Move down" custom actions, so reordering is not limited to
 * a gesture.
 *
 * The list reorders **under the finger** as the dragged row crosses its neighbours, and the order is
 * handed to [onReorder] once, when the finger lifts. A drag that ends where it started writes
 * nothing.
 *
 * Deliberate limitation: there is no edge auto-scroll. The lists this serves fit on a phone, and an
 * auto-scroll that races the drag is more failure modes than the feature needs today.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    handleDescription: String,
    moveUpLabel: String,
    moveDownLabel: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    spacing: Dp = 10.dp,
    itemContent: @Composable (item: T, handle: Modifier, isDragging: Boolean) -> Unit,
) {
    var displayItems by remember { mutableStateOf(items) }
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var orderAtDragStart by remember { mutableStateOf<List<T>>(emptyList()) }
    val heights = remember { mutableStateMapOf<Any, Int>() }

    // A write (or another screen) changed the list: adopt it, unless a finger is down.
    LaunchedEffect(items) {
        if (draggingIndex == null) displayItems = items
    }

    fun itemHeight(index: Int): Float = (heights[keyOf(displayItems[index])] ?: 0).toFloat()

    fun startDrag(index: Int) {
        draggingIndex = index
        dragOffset = 0f
        orderAtDragStart = displayItems
    }

    /**
     * Move the dragged row in the list as it crosses a neighbour, adjusting the visual offset by the
     * neighbour's height so the row stays exactly under the finger. The threshold is the centre
     * distance: half the dragged row plus half the neighbour.
     */
    fun drag(deltaY: Float) {
        dragOffset += deltaY
        var index = draggingIndex ?: return
        val draggedHeight = itemHeight(index)

        while (index < displayItems.lastIndex) {
            val nextHeight = itemHeight(index + 1)
            val threshold = (draggedHeight + nextHeight) / 2f
            if (dragOffset <= threshold) break
            displayItems = displayItems.moved(index, index + 1)
            dragOffset -= nextHeight
            index += 1
        }
        while (index > 0) {
            val previousHeight = itemHeight(index - 1)
            val threshold = (draggedHeight + previousHeight) / 2f
            if (dragOffset >= -threshold) break
            displayItems = displayItems.moved(index, index - 1)
            dragOffset += previousHeight
            index -= 1
        }
        draggingIndex = index
    }

    fun finishDrag(commit: Boolean) {
        val changed = commit && displayItems.map(keyOf) != orderAtDragStart.map(keyOf)
        draggingIndex = null
        dragOffset = 0f
        if (!commit) {
            // A cancelled drag is not an order: put the rows back where they were.
            displayItems = orderAtDragStart
        } else if (changed) {
            onReorder(displayItems)
        }
    }

    fun moveByAccessibility(from: Int, to: Int) {
        if (draggingIndex != null || to !in displayItems.indices || from == to) return
        val moved = displayItems.moved(from, to)
        displayItems = moved
        onReorder(moved)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
    ) {
        displayItems.forEachIndexed { index, item ->
            key(keyOf(item)) {
                val dragging = index == draggingIndex
                val currentIndex by rememberUpdatedState(index)
                val scale by animateFloatAsState(
                    targetValue = if (dragging) 1.02f else 1f,
                    animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    label = "reorder-scale",
                )

                val handle = Modifier
                    // A tap on the handle must not fall through to the row's own click.
                    .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startDrag(currentIndex) },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                drag(dragAmount.y)
                            },
                            onDragEnd = { finishDrag(commit = true) },
                            onDragCancel = { finishDrag(commit = false) },
                        )
                    }
                    .semantics {
                        contentDescription = handleDescription
                        customActions = listOf(
                            CustomAccessibilityAction(moveUpLabel) {
                                moveByAccessibility(currentIndex, currentIndex - 1)
                                true
                            },
                            CustomAccessibilityAction(moveDownLabel) {
                                moveByAccessibility(currentIndex, currentIndex + 1)
                                true
                            },
                        )
                    }

                Column(
                    modifier = Modifier
                        .onGloballyPositioned { coordinates -> heights[keyOf(item)] = coordinates.size.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            scaleX = scale
                            scaleY = scale
                        }
                        .then(if (dragging) Modifier.shadow(12.dp, GgymCardShape) else Modifier),
                ) {
                    itemContent(item, handle, dragging)
                }
            }
        }
    }
}

/** A list with one element moved, shared by the drag and the accessibility actions. */
internal fun <T> List<T>.moved(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
