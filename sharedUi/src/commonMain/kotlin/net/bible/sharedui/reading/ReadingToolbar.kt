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

package net.bible.sharedui.reading

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.bible.sharedcore.reading.ToolbarButton
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedcore.reading.fitToolbarButtons
import net.bible.sharedui.components.AbActionIconSize
import net.bible.sharedui.strings.LocalStrings
import kotlin.math.abs

/**
 * The set of icons [ReadingToolbar] draws, supplied by the host as [Painter]s (never
 * `R.drawable`/`ImageVector` resource ids) so this file stays iOS-clean. The host (Task 5) wires
 * these to the same drawables the classic View toolbar uses (`main_bible_view.xml`).
 */
data class ReadingToolbarIcons(
    val home: Painter,
    val search: Painter,
    val speak: Painter,
    val strongs: Painter,
    val bible: Painter,
    val commentary: Painter,
    val workspace: Painter,
    val overflow: Painter,
)

/**
 * All the interaction callbacks [ReadingToolbar] can invoke. Mirrors the classic
 * `MainBibleActivity` toolbar's click/long-click/fling handlers 1:1 (see `updateActions()` and
 * `setupToolbarFlingDetection()`), minus anything view-specific (no `View`/`MotionEvent` in the
 * signatures — the host resolves those on the Android side).
 */
data class ReadingToolbarCallbacks(
    val onHome: () -> Unit,
    val onTitleTap: () -> Unit,
    val onTitleLongPress: () -> Unit,
    val onTitleFlingVertical: () -> Unit,
    val onTitleFlingHorizontal: (forward: Boolean) -> Unit,
    val onBible: () -> Unit,
    val onBibleLong: () -> Unit,
    val onCommentary: () -> Unit,
    val onCommentaryLong: () -> Unit,
    val onStrongs: () -> Unit,
    val onStrongsLong: () -> Unit,
    val onSearch: () -> Unit,
    val onSpeak: () -> Unit,
    val onSpeakLong: () -> Unit,
    val onWorkspace: () -> Unit,
    val onOverflow: () -> Unit,
)

/** Height of the toolbar row — matches the classic `@dimen/toolbar_height` (56dp). */
private val ToolbarHeight = 56.dp

/** Touch-target width for a single icon button (home / quick button / overflow). */
private val ToolbarButtonWidth = 48.dp

/**
 * Stateless port of the classic `MainBibleActivity` toolbar (`main_bible_view.xml`'s
 * `toolbarLayout`): a home (drawer) button, a tappable title block (page title + document title +
 * sync indicator), the width-fitted "quick" buttons (Bible/Commentary/Strongs/Search/Speak/
 * Workspace — see [fitToolbarButtons]), and an always-present overflow button.
 *
 * The available width for [fitToolbarButtons] is measured from this composable's own laid-out
 * width via [BoxWithConstraints] (mirroring classic `updateActions()`'s
 * `resources.displayMetrics.widthPixels` full-screen-width budget).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingToolbar(
    state: ToolbarState,
    icons: ReadingToolbarIcons,
    callbacks: ReadingToolbarCallbacks,
    searchMoreRecent: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
            .height(ToolbarHeight)
            .background(MaterialTheme.colorScheme.surface),
    ) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val buttons = remember(state, widthPx, density.density, searchMoreRecent) {
            fitToolbarButtons(state, widthPx, density.density, searchMoreRecent)
        }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            ToolbarIconButton(
                icon = icons.home,
                // TODO: no LocalStrings field for this yet — keep literal until one is added.
                contentDescription = "Menu",
                onClick = callbacks.onHome,
            )
            ReadingToolbarTitle(state, callbacks, Modifier.weight(1f).fillMaxHeight())
            buttons.forEach { button -> QuickToolbarButton(button, state, icons, callbacks) }
            ToolbarIconButton(
                icon = icons.overflow,
                // TODO: no LocalStrings field for this yet — keep literal until one is added.
                contentDescription = "Options",
                onClick = callbacks.onOverflow,
            )
        }
    }
}

@Composable
private fun QuickToolbarButton(
    button: ToolbarButton,
    state: ToolbarState,
    icons: ReadingToolbarIcons,
    callbacks: ReadingToolbarCallbacks,
) {
    val strings = LocalStrings.current
    when (button) {
        ToolbarButton.BIBLE -> ToolbarIconButton(icons.bible, strings.bible, callbacks.onBible, callbacks.onBibleLong)
        // TODO: no LocalStrings field for this yet — keep literal until one is added.
        ToolbarButton.COMMENTARY ->
            ToolbarIconButton(icons.commentary, "Commentary", callbacks.onCommentary, callbacks.onCommentaryLong)
        ToolbarButton.STRONGS -> ToolbarIconButton(
            icon = icons.strongs,
            // TODO: no LocalStrings field for this yet — keep literal until one is added.
            contentDescription = "Strong's numbers",
            onClick = callbacks.onStrongs,
            onLongClick = callbacks.onStrongsLong,
            alpha = if (state.strongsMode == 0) 0.5f else 1f,
        )
        ToolbarButton.SEARCH -> ToolbarIconButton(icons.search, strings.search, callbacks.onSearch)
        ToolbarButton.SPEAK -> ToolbarIconButton(icons.speak, strings.speak, callbacks.onSpeak, callbacks.onSpeakLong)
        // TODO: no LocalStrings field for this yet — keep literal until one is added.
        ToolbarButton.WORKSPACE -> ToolbarIconButton(icons.workspace, "Workspace", callbacks.onWorkspace)
    }
}

/** A single toolbar icon button: fixed touch-target width, full-height, tap + optional long-press. */
@Composable
private fun ToolbarIconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    alpha: Float = 1f,
) {
    Row(
        modifier = Modifier
            .fillMaxHeight()
            .width(ToolbarButtonWidth)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(AbActionIconSize).alpha(alpha),
        )
    }
}

/**
 * The tappable title block: page title (large, single line, ellipsized) over the document title
 * (small, secondary) with a sync indicator alongside it while [ToolbarState.syncRunning]. Gestures
 * mirror classic `setupToolbarFlingDetection()`: a plain tap re-opens the key chooser, a long-press
 * opens the document chooser, and a fling is routed to a vertical (workspace selector) or
 * horizontal (cycle workspace) callback depending on its dominant axis — see [detectTitleGestures].
 */
@Composable
private fun ReadingToolbarTitle(state: ToolbarState, callbacks: ReadingToolbarCallbacks, modifier: Modifier = Modifier) {
    // Keyed on Unit (stable) rather than `callbacks` — a recomposition mid-gesture (e.g.
    // state.syncRunning flipping) must not restart the gesture-detector coroutine and abort an
    // in-flight tap/long-press/fling. rememberUpdatedState lets the long-lived gesture block
    // always read the LATEST callbacks without needing pointerInput to be re-keyed on them.
    val currentCallbacks = rememberUpdatedState(callbacks)
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .pointerInput(Unit) { detectTitleGestures(currentCallbacks) },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = state.pageTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.documentTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (state.syncRunning) {
                SyncIndicator(Modifier.padding(start = 6.dp))
            }
        }
    }
}

/**
 * Small spinner shown next to the document title while a background sync is running. Under
 * inspection (Roborazzi goldens / previews) it renders a frozen determinate frame instead of the
 * animated indeterminate one — same reasoning as [net.bible.sharedui.components.AbLoadingIndicator]:
 * `LocalInspectionMode` does not freeze Compose's `InfiniteTransition`, so an indeterminate spinner
 * would capture a non-deterministic frame and make the "syncing" golden flaky. The frozen fraction
 * is a non-zero [FrozenSyncIndicatorProgress] — a `progress = 0f` frame draws a zero-sweep (fully
 * invisible) arc, which would make the "syncing" golden indistinguishable from "not syncing".
 */
@Composable
private fun SyncIndicator(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) {
        CircularProgressIndicator(
            progress = { FrozenSyncIndicatorProgress },
            modifier = modifier.size(10.dp),
            strokeWidth = 1.5.dp,
        )
    } else {
        CircularProgressIndicator(modifier = modifier.size(10.dp), strokeWidth = 1.5.dp)
    }
}

/** Frozen progress fraction [SyncIndicator] draws under [LocalInspectionMode] — see its kdoc. */
private const val FrozenSyncIndicatorProgress = 0.65f

/** Distance (px) a drag must cover on its dominant axis before it counts as a fling, not a tap-adjacent wobble. */
private const val MinFlingDistanceDp = 40

/**
 * Single hand-rolled pointer-input gesture recognizer for the title block, combining tap,
 * long-press and directional fling detection in ONE gesture (rather than layering
 * `detectTapGestures`/`detectDragGestures` in separate `pointerInput` modifiers, which would race
 * each other for the same down event). Distance-only thresholds are used for the fling axis
 * decision (loosely mirroring classic `setupToolbarFlingDetection`'s distance+velocity check —
 * exact velocity parity is not required for this port).
 *
 * [currentCallbacks] is a [State] (from [rememberUpdatedState] at the call site) rather than a
 * plain [ReadingToolbarCallbacks], so the caller can key its `pointerInput` on a stable value
 * (`Unit`) without this long-lived gesture loop ever invoking a stale lambda.
 */
private suspend fun PointerInputScope.detectTitleGestures(currentCallbacks: State<ReadingToolbarCallbacks>) {
    val touchSlop = viewConfiguration.touchSlop
    val longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis
    val minFlingDistancePx = MinFlingDistanceDp.dp.toPx()

    coroutineScope {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val pointerId = down.id
            var totalDx = 0f
            var totalDy = 0f
            var isDrag = false
            var longPressFired = false

            // Single long-press deadline armed ONCE from the initial down (mirrors Compose
            // Foundation's `detectTapGestures`) instead of a per-pointer-event timeout that
            // measured "time since the last event" — touch jitter (ACTION_MOVE) restarted that
            // clock on every move, so it could starve the long-press indefinitely. Cancelled as
            // soon as the gesture resolves into a drag (a fling in progress is never also a
            // long-press) or the pointer lifts/cancels.
            val longPressJob = launch {
                delay(longPressTimeoutMillis)
                longPressFired = true
                currentCallbacks.value.onTitleLongPress()
            }

            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change: PointerInputChange = event.changes.firstOrNull { it.id == pointerId } ?: break
                    if (!change.pressed) {
                        change.consume()
                        break
                    }
                    val delta = change.positionChange()
                    totalDx += delta.x
                    totalDy += delta.y
                    if (!isDrag && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                        isDrag = true
                        longPressJob.cancel()
                    }
                    change.consume()
                }
            } finally {
                longPressJob.cancel()
            }

            if (!longPressFired) {
                if (isDrag) {
                    if (abs(totalDy) > abs(totalDx) && abs(totalDy) > minFlingDistancePx) {
                        currentCallbacks.value.onTitleFlingVertical()
                    } else if (abs(totalDx) > minFlingDistancePx) {
                        currentCallbacks.value.onTitleFlingHorizontal(totalDx < 0)
                    }
                } else {
                    currentCallbacks.value.onTitleTap()
                }
            }
        }
    }
}
