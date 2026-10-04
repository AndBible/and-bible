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

package net.bible.sharedcore.reading

/**
 * One row of the reading view's main navigation drawer.
 *
 * [label] is an already-resolved display string, NOT a resource key — the same convention as
 * [OptionsMenuItem.label] (resolved host-side in `OptionsMenuStateBuilder.build`). Carrying keys
 * would mean adding 26 entries to the `:sharedUi` `Strings` interface for no benefit.
 *
 * [iconKey] is the classic drawable name (e.g. `"ic_search_24dp"`); the composable resolves it to a
 * `Painter` through a host-supplied lambda, the same shape `mountComposeView` uses for `windowIcon`.
 *
 * There is deliberately no `visible` flag: hidden rows are filtered out by [DrawerMenu.build],
 * mirroring the classic `MenuItem.isVisible = false` writes.
 */
data class DrawerItem(
    val id: String,
    val label: String,
    val iconKey: String,
    val enabled: Boolean = true,
)

/** A drawer section. [title] is `null` for the untitled top tier. */
data class DrawerGroup(
    val title: String?,
    val items: List<DrawerItem>,
)

/** The whole drawer: header app name, sections, and the version-text footer. */
data class DrawerMenuState(
    val appName: String,
    val groups: List<DrawerGroup>,
    val versionText: String,
) {
    companion object {
        val EMPTY = DrawerMenuState(appName = "", groups = emptyList(), versionText = "")
    }
}

/** A row as the host declares it, before visibility filtering. */
data class DrawerItemSpec(
    val id: String,
    val label: String,
    val iconKey: String,
    val enabled: Boolean = true,
    val visible: Boolean = true,
)

/** A section as the host declares it. */
data class DrawerGroupSpec(
    val title: String?,
    val items: List<DrawerItemSpec>,
)

object DrawerMenu {
    /**
     * Applies the classic visibility rules: drops every `visible = false` row (mirroring the
     * classic `isVisible = false` writes) and then drops any section left with no rows. Declaration
     * order is preserved throughout — the drawer's order is parity-relevant.
     */
    fun build(
        appName: String,
        versionText: String,
        groups: List<DrawerGroupSpec>,
    ): DrawerMenuState = DrawerMenuState(
        appName = appName,
        versionText = versionText,
        groups = groups.mapNotNull { group ->
            val items = group.items
                .filter { it.visible }
                .map { DrawerItem(id = it.id, label = it.label, iconKey = it.iconKey, enabled = it.enabled) }
            if (items.isEmpty()) null else DrawerGroup(title = group.title, items = items)
        },
    )
}
