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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The one `MenuItemColors` every popup-menu row in this app uses.
 *
 * Material3's default gives a row's LABEL `onSurface` and its ICONS `onSurfaceVariant`, so an
 * iconned menu draws its two halves in two colours — visible enough that it reads as a bug rather
 * than as emphasis, and it was reported as one. Here the icons take the label's colour.
 *
 * What deliberately does NOT change: the menu's CONTAINER colour (M3's `surfaceContainer` plus
 * tonal elevation). A popup is a surface of its own, so it is meant to differ from the bar that
 * opened it; the reading toolbar in particular computes literal workspace-derived colours outside
 * the colour scheme (`ReadingToolbar.kt:309-346`) that no scheme token could match anyway.
 * Disabled colours are left at M3's defaults so a disabled row still reads as disabled.
 *
 * Not `@ReadOnlyComposable`: `MenuDefaults.itemColors` in the resolved Material3 version is not
 * itself `@ReadOnlyComposable`-callable, so annotating this function fails to compile.
 */
@Composable
fun abMenuItemColors(): MenuItemColors = MenuDefaults.itemColors(
    textColor = MaterialTheme.colorScheme.onSurface,
    leadingIconColor = MaterialTheme.colorScheme.onSurface,
    trailingIconColor = MaterialTheme.colorScheme.onSurface,
)

/** The leading-icon slot's size, and the width reserved when [AbMenuItem.reserveIconSlot] is set. */
private val AbMenuIconSize = 24.dp

/**
 * One row of a popup menu — the single seam every menu in this app goes through.
 *
 * Why a seam at all: before F49 this app called `DropdownMenuItem` directly from ~70 places and
 * grew FIVE different ways to show a selected row (a trailing `Check`; a leading `Checkbox`; a
 * leading `CheckBox`/`CheckBoxOutlineBlank` vector; a `"✓ "` prefix baked into the label string;
 * and nothing at all). This function owns that decision once: a checked row gets a trailing
 * [Icons.Default.Check], which is the reading-view 3-dot menu's long-standing look.
 *
 * [icon] is a SLOT rather than an `ImageVector`/`Painter` parameter so `:sharedUi` callers can pass
 * a Material icon and `:app` callers a `painterResource` drawable through one function — the four
 * `:app` menus have no Material icons on their classpath but do have the classic drawables.
 *
 * [reserveIconSlot] draws an empty icon-sized box for a row that genuinely has no icon, keeping its
 * label on the same left edge as its iconned neighbours. Material3 inserts the leading-icon box on
 * `leadingIcon != null`, so without this a mixed level steps in and out. This mirrors classic's
 * `MenuPopupHelper.setForceShowIcon(true)` (a `null` icon there became `mEmptyIcon`/`INVISIBLE`,
 * never `GONE`) — see [net.bible.sharedui.reading.MenuIconRows] for the same rationale applied to
 * the reading-view menus' data-driven rows. It defaults to `false` because the F49 migration gives
 * essentially every row an icon; it is the escape hatch, not the norm.
 *
 * [trailing] is for a non-state affordance such as a submenu chevron. A checked row's check WINS
 * over it: no row in this app is both a submenu and a toggle, and if one ever is, its state is the
 * more important of the two to show.
 */
@Composable
fun AbMenuItem(
    text: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    checkable: Boolean = false,
    checked: Boolean = false,
    enabled: Boolean = true,
    reserveIconSlot: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val leading: (@Composable () -> Unit)? = when {
        icon != null -> {
            { Box(Modifier.size(AbMenuIconSize), contentAlignment = Alignment.Center) { icon() } }
        }
        reserveIconSlot -> {
            { Box(Modifier.size(AbMenuIconSize)) }
        }
        else -> null
    }
    val trailingSlot: (@Composable () -> Unit)? = when {
        checkable && checked -> {
            { Icon(Icons.Default.Check, contentDescription = null) }
        }
        else -> trailing
    }
    DropdownMenuItem(
        text = { Text(text) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = leading,
        trailingIcon = trailingSlot,
        colors = abMenuItemColors(),
    )
}

/**
 * The leading icon every "Help" row in this app uses. A single shared value so the icon can't
 * drift screen to screen — before this it was hand-copied, verbatim, at five call sites
 * ([net.bible.sharedui.reading.ReadingToolbar], [net.bible.sharedui.search.EpubSearchScreen],
 * [net.bible.sharedui.progress.ReadingProgressScreen], [net.bible.sharedui.speak.SpeakSettingsContent],
 * [net.bible.sharedui.speak.AdvancedSpeakSettingsContent]) — a sixth screen would have made it six.
 */
val AbHelpMenuIcon: @Composable () -> Unit = {
    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null)
}
