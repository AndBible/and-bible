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

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuItemColors
import androidx.compose.runtime.Composable

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
