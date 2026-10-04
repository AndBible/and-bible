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

package net.bible.sharedcore.window

/** Leading control shown at the start of the window-tab rail. */
sealed interface RailLeading {
    /** A window is maximised: only an "unmaximise" affordance is shown. */
    data object Unmaximise : RailLeading

    /** Exactly one (non-closed) window: offer adding a new one. */
    data object AddWindow : RailLeading

    /** Multiple windows: toggles whether the rest of the rail (the tabs) is shown. */
    data class CollapseToggle(val expanded: Boolean) : RailLeading
}

/** One item in the window-tab rail's entry list. */
sealed interface RailEntry {
    data class WindowTab(val window: WindowSnapshot, val isActive: Boolean) : RailEntry
    data object GroupSeparator : RailEntry
}

/**
 * The rail model for the window-tab bar, derived purely from [WindowLayoutState].
 *
 * @property showButtons false when maximised or single-windowed (nothing to show), or when
 *   multi-windowed but collapsed via [RailLeading.CollapseToggle]; true when the [entries]
 *   should actually be rendered.
 */
data class WindowTabBarModel(
    val leading: RailLeading,
    val showButtons: Boolean,
    val entries: List<RailEntry>,
)

/**
 * Derives the window-tab rail model from the window layout. Mirrors classic
 * `SplitBibleArea.rebuildRestoreButtons()`:
 * - maximised → [RailLeading.Unmaximise], no entries, buttons hidden.
 * - single non-closed window → [RailLeading.AddWindow], no entries, buttons hidden.
 * - multiple → [RailLeading.CollapseToggle], entries = non-closed windows grouped
 *   pinned (non-links) → non-pinned (non-links) → links, in [WindowLayoutState.windows]
 *   display order within each group, with a single [RailEntry.GroupSeparator] between
 *   adjacent non-empty groups.
 */
fun buildWindowTabBar(layout: WindowLayoutState): WindowTabBarModel {
    if (layout.maximizedWindowId != null) {
        return WindowTabBarModel(leading = RailLeading.Unmaximise, showButtons = false, entries = emptyList())
    }

    val openWindows = layout.windows.filter { it.state != WindowStateValue.CLOSED }

    if (openWindows.size <= 1) {
        return WindowTabBarModel(leading = RailLeading.AddWindow, showButtons = false, entries = emptyList())
    }

    val pinnedWindows = openWindows.filter { it.isPinMode && !it.isLinksWindow }
    val nonPinnedWindows = openWindows.filter { !it.isPinMode && !it.isLinksWindow }
    val linksWindows = openWindows.filter { it.isLinksWindow }

    val entries = mutableListOf<RailEntry>()
    fun addGroup(group: List<WindowSnapshot>) {
        if (group.isEmpty()) return
        if (entries.isNotEmpty()) entries.add(RailEntry.GroupSeparator)
        group.forEach { window ->
            entries.add(RailEntry.WindowTab(window = window, isActive = window.id == layout.activeWindowId))
        }
    }
    addGroup(pinnedWindows)
    addGroup(nonPinnedWindows)
    addGroup(linksWindows)

    return WindowTabBarModel(
        leading = RailLeading.CollapseToggle(expanded = layout.restoreButtonsVisible),
        showButtons = layout.restoreButtonsVisible,
        entries = entries,
    )
}
