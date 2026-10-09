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

package net.bible.sharedui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic AndBible accents (bookmark, active-window indicator, helper lines) — colored in
 *  NORMAL/COLOR_EINK, gray in BW, ink in MONOCHROME.
 *  monoDisabled: disabled/off content, #808080 in MONOCHROME, onSurface at 38% elsewhere. Read via LocalAbColors so a mode switch recolors uniformly. */
data class AbColors(
    val bookmark: Color,
    val activeWindow: Color,
    val helperLine: Color,
    val monoDisabled: Color,
)

val LocalAbColors = staticCompositionLocalOf<AbColors> { error("LocalAbColors not provided") }
val LocalDisableAnimations = staticCompositionLocalOf { false }
