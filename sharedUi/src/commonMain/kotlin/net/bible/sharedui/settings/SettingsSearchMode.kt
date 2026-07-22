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

package net.bible.sharedui.settings

/**
 * How a searchable [AbSettingsScreen] presents its search field.
 *
 * [AlwaysVisible] (default) shows a compact search field permanently below the top app bar — a modest
 * M3 modernization over the classic collapsible SearchView. [CollapsibleIcon] mirrors the classic:
 * a search action-icon in the app bar that expands the field on tap and clears the query on collapse.
 * The choice is one parameter so it can be flipped after on-device A/B without touching call sites.
 */
enum class SettingsSearchMode { AlwaysVisible, CollapsibleIcon }
