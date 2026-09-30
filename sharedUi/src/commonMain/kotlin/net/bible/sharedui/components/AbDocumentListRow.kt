/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with AndBible.
 * If not, see http://www.gnu.org/licenses/.
 */
package net.bible.sharedui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Widths, in px, of the left and right system-gesture edges of the window (the back gesture's zones). */
data class HorizontalGestureEdges(val leftPx: Float, val rightPx: Float)

/**
 * Whether a press at [windowX] (window coordinates) starts inside a horizontal gesture edge. The
 * boundaries belong to the content, so a zero-width edge never rejects anything.
 */
fun isInHorizontalGestureEdge(windowX: Float, windowWidth: Float, edges: HorizontalGestureEdges): Boolean =
    windowX < edges.leftPx || windowX > windowWidth - edges.rightPx

/**
 * The anatomy every document list row shares: a leading slot (a checkbox in selection mode, or an
 * icon that may carry a corner badge), a two-line title/subtitle column that takes the remaining
 * width, and a trailing slot for status and actions.
 *
 * Extracted in round 17e-2 from `DocumentRow`, whose measurements this reproduces exactly —
 * 16dp horizontal / 12dp vertical padding, a 16dp gap after the leading slot, `bodyLarge` title at
 * two lines and `bodySmall` subtitle — so the extraction changes no pixel. The cloud document list
 * fills the same shape, which is what makes the two screens read as one app.
 *
 * The leading slot is a `BoxScope` so a caller can `align(Alignment.BottomEnd)` a badge inside it
 * without the row having to know what the badge is.
 *
 * [gestureEdges] is a test seam; null reads `WindowInsets.systemGestures`.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AbDocumentListRow(
    title: String,
    subtitle: AnnotatedString,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleMaxLines: Int = 1,
    leading: @Composable BoxScope.() -> Unit,
    trailing: @Composable RowScope.() -> Unit,
    gestureEdges: HorizontalGestureEdges? = null,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val systemEdges = WindowInsets.systemGestures
    val edges = gestureEdges ?: HorizontalGestureEdges(
        leftPx = systemEdges.getLeft(density, layoutDirection).toFloat(),
        rightPx = systemEdges.getRight(density, layoutDirection).toFloat(),
    )
    val windowWidth = LocalWindowInfo.current.containerSize.width.toFloat()
    // F73 (fix batch 3 §2.2.2): where in the window the current press started. Read in the Initial
    // pass and never consumed, so the click and the system's own gesture handling are untouched;
    // only the click CALLBACK is withheld for a press that began inside a gesture edge.
    var rowLeftInWindow by remember { mutableFloatStateOf(0f) }
    var pressStartedInEdge by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { rowLeftInWindow = it.positionInWindow().x }
            .pointerInput(edges, windowWidth) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pressStartedInEdge = windowWidth > 0f &&
                        isInHorizontalGestureEdge(rowLeftInWindow + down.position.x, windowWidth, edges)
                    // Reset when the gesture ends (Final pass: after combinedClickable has fired), so a
                    // later non-pointer activation (TalkBack, keyboard) is not dropped by a stale flag.
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                    } while (event.changes.any { it.pressed })
                    pressStartedInEdge = false
                }
            }
            .combinedClickable(onClick = { if (!pressStartedInEdge) onClick() }, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, content = leading)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = subtitleMaxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing()
    }
}
