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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import net.bible.sharedcore.window.RailEntry
import net.bible.sharedcore.window.RailLeading
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.WindowTabBarModel

private val LeadingControlSize = 40.dp
private val RailEntrySpacing = 6.dp
private val GroupSeparatorWidth = 1.dp
private val GroupSeparatorHeight = 24.dp

/** Classic `window_bar_background` (`res/drawable/window_bar_background.xml`): only the top-start
 *  corner is rounded, 6dp. `topStart`, not `topLeft`, is the deliberate RTL-correct reading of the
 *  same intent — it mirrors to the visual top-right in an RTL layout, where classic's hard-coded
 *  `topLeftRadius` would not. */
private val RailCornerRadius = 6.dp
/** Classic `window_bar_background`'s `padding left=5dp top=2dp` (right/bottom are 0). */
private val RailPaddingStart = 5.dp
private val RailPaddingTop = 2.dp

/**
 * The window-tab rail: a bottom-end-aligned bar mirroring classic `SplitBibleArea`'s restore
 * button strip (`app/src/main/java/net/bible/android/view/activity/page/screen/SplitBibleArea.kt`
 * `rebuildRestoreButtons()`) — a dumb renderer of [WindowTabBarModel] (the pure derivation from
 * `WindowLayoutState`, Task 4), reusing [WindowButton] (Task 3) for every tab.
 *
 * Layout: a [Row] (`wrapContentWidth`, [Arrangement.End], background-bearing — see below) always
 * shows the leading control for [WindowTabBarModel.leading] first, then — while
 * [WindowTabBarModel.showButtons] — a [LazyRow] rendering [WindowTabBarModel.entries] (window
 * tabs interleaved with group separators), itself packed to the end via
 * `Arrangement.spacedBy(RailEntrySpacing, alignment = Alignment.End)`. The outer [Row] is
 * `wrapContentWidth` rather than `fillMaxWidth`, and the [LazyRow] sits in an
 * `AnimatedVisibility(Modifier.weight(1f, fill = false))` (so it measures to its actual tab-strip
 * width, never stretching to fill) — together these let the WHOLE bar shrink to just what it
 * needs and sit at its container's end edge, restoring classic `restoreButtonsContainer`'s
 * `wrap_content`-width, `bottom`+`end`-only-constrained container
 * (`res/layout/split_bible_area.xml:31-38`) and its inner row's `layout_gravity="end"` (`:39-44`) —
 * the bar FLOATS over the panes ([SplitContent]'s `railOverlay`) instead of taking a layout band
 * from them. When hidden (single/maximised, or collapsed), the lone leading control is pushed to
 * the row's end by [Arrangement.End] — a small control sitting at the bottom-end corner, exactly
 * like classic's translated-off-screen restore bar leaves only its arrow visible. Showing/hiding
 * the [LazyRow] is wrapped in [AnimatedVisibility] (per [RailLeading.CollapseToggle]) so the strip
 * slides rather than jump-cuts.
 *
 * The background (`Modifier.background(MaterialTheme.colorScheme.surfaceVariant,
 * RoundedCornerShape(topStart = [RailCornerRadius]))`, padded by [RailPaddingStart]/
 * [RailPaddingTop]) restores classic `window_bar_background`
 * (`res/drawable/window_bar_background.xml`): a rectangle with only ONE corner rounded and
 * asymmetric padding. `topStart` — not `topLeft` — is used deliberately: it mirrors to the visual
 * top-right corner in RTL, where classic's hard-coded `topLeftRadius` does not. Since M3 theming
 * (not a hard-coded hue) drives the colour, [net.bible.sharedui.theme.AbTheme]'s monochrome/e-ink
 * `displayColorMode` grayscales it automatically, same as every other M3-coloured surface in this
 * port.
 *
 * Each [WindowButton] is keyed by its window id (and each separator by its position) — required
 * so [WindowButton]'s `pointerInput(Unit)`-based tap/long-press keeps a stable identity across
 * recomposition (same rule as Task 3; the LazyRow would otherwise risk stale captured callbacks
 * after a reorder, the same bug class fixed for Tuya Lights' reorderable light list).
 *
 * @param windowLabel host-supplied per-window label (classic doc initials/ordinal) — kept out of
 *   this iOS-clean module, same seam as [WindowButton]'s `label`.
 * @param windowIcon host-supplied per-window doc-type [Painter]; `null` (default) shows none.
 * @param windowTopLabel host-supplied per-window tiny top-row label (classic `topButtonText`,
 *   `pageManager.titleText`) — same host-supplied, non-`@Composable` seam as [windowLabel]/
 *   [windowIcon]; forwarded verbatim to each [WindowButton]'s `topLabel` (Task 3). `null` (the
 *   default, and per-window whenever the host has no title for that window) renders no top row.
 */
@Composable
fun WindowTabBar(
    model: WindowTabBarModel,
    onRestore: (windowId: String) -> Unit,
    onWindowLongPress: (windowId: String) -> Unit,
    onAddWindow: () -> Unit,
    onUnMaximise: () -> Unit,
    onToggleCollapse: () -> Unit,
    windowLabel: (WindowSnapshot) -> String,
    windowIcon: (WindowSnapshot) -> Painter? = { null },
    windowTopLabel: (WindowSnapshot) -> String? = { null },
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .wrapContentWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(topStart = RailCornerRadius),
            )
            .padding(start = RailPaddingStart, top = RailPaddingTop, end = 0.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailLeadingControl(
            leading = model.leading,
            onAddWindow = onAddWindow,
            onUnMaximise = onUnMaximise,
            onToggleCollapse = onToggleCollapse,
        )

        AnimatedVisibility(visible = model.showButtons, modifier = Modifier.weight(1f, fill = false)) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(RailEntrySpacing, alignment = Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                itemsIndexed(
                    items = model.entries,
                    key = { index, entry ->
                        when (entry) {
                            is RailEntry.WindowTab -> entry.window.id
                            RailEntry.GroupSeparator -> "sep-$index"
                        }
                    },
                ) { _, entry ->
                    when (entry) {
                        is RailEntry.WindowTab -> {
                            val window = entry.window
                            WindowButton(
                                label = windowLabel(window),
                                isActive = entry.isActive,
                                isMinimised = window.state == WindowStateValue.MINIMISED,
                                isPinned = window.isPinMode,
                                isLinks = window.isLinksWindow,
                                // WindowSnapshot.syncGroup is the raw 0-based Window.syncGroup;
                                // WindowButton requires the 1-based value it displays (classic
                                // parity — see WindowButton's kdoc), same as the sibling pane
                                // caller (ComposeReadingViewHost.kt's PaneWindowButtonOverlay).
                                syncGroup = if (window.isSynchronised) window.syncGroup + 1 else 0,
                                mode = WindowButtonMode.Rail,
                                onClick = { onRestore(window.id) },
                                onLongPress = { onWindowLongPress(window.id) },
                                leadingIcon = windowIcon(window),
                                topLabel = windowTopLabel(window),
                            )
                        }
                        RailEntry.GroupSeparator -> GroupSeparatorDivider()
                    }
                }
            }
        }
    }
}

/**
 * The single leading control, switched on [RailLeading]: [RailLeading.Unmaximise] (a window is
 * maximised) shows a "close fullscreen" glyph; [RailLeading.AddWindow] (exactly one window) shows
 * "+"; [RailLeading.CollapseToggle] shows a chevron whose direction flips with
 * [RailLeading.CollapseToggle.expanded] — pointing towards the strip while expanded (tap to
 * collapse that way) and back once collapsed (tap to reopen), so the arrow always points the
 * direction the tab strip is about to move.
 */
@Composable
private fun RailLeadingControl(
    leading: RailLeading,
    onAddWindow: () -> Unit,
    onUnMaximise: () -> Unit,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (leading) {
        RailLeading.Unmaximise -> RailIconButton(icon = Icons.Filled.CloseFullscreen, onClick = onUnMaximise, modifier = modifier)
        RailLeading.AddWindow -> RailIconButton(icon = Icons.Filled.Add, onClick = onAddWindow, modifier = modifier)
        is RailLeading.CollapseToggle -> RailIconButton(
            icon = if (leading.expanded) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onClick = onToggleCollapse,
            modifier = modifier,
        )
    }
}

@Composable
private fun RailIconButton(icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(LeadingControlSize)) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A small vertical divider marking the boundary between adjacent tab groups (pinned/plain/links). */
@Composable
private fun GroupSeparatorDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(horizontal = 2.dp)
            .width(GroupSeparatorWidth)
            .height(GroupSeparatorHeight)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
