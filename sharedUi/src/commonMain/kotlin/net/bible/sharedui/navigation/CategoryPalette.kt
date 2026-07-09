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
package net.bible.sharedui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import net.bible.sharedcore.theme.accentArgbFor
import net.bible.sharedui.theme.LocalDisplayColorMode

// The classic getBookColorAndGroup palette (ARGB), one per category 0..9.
private val CATEGORY_BASE = intArrayOf(
    0xFFCCCCFE.toInt(), // 0 Pentateuch
    0xFFFECC9B.toInt(), // 1 History
    0xFF99FF99.toInt(), // 2 Wisdom
    0xFFFF99FF.toInt(), // 3 Major prophets
    0xFFFFFECD.toInt(), // 4 Minor prophets
    0xFFFF9703.toInt(), // 5 Gospel
    0xFF0099FF.toInt(), // 6 Acts
    0xFFFFFF31.toInt(), // 7 Pauline
    0xFF67CC66.toInt(), // 8 General epistles
    0xFFFE33FF.toInt(), // 9 Revelation
)
private const val OTHER_BASE = 0xFF0099FF.toInt() // classic OTHER_COLOR = Acts blue

/** Category color for a [GridButton.colorGroup]; grays out in BW, stays colored in COLOR_EINK. */
@Composable
fun categoryColor(colorGroup: Int): Color {
    val base = if (colorGroup in CATEGORY_BASE.indices) CATEGORY_BASE[colorGroup] else OTHER_BASE
    return Color(accentArgbFor(base, LocalDisplayColorMode.current))
}

// Progress-bar hues (classic green/gold), also e-ink-aware.
@Composable
fun readingColor(): Color = Color(accentArgbFor(0xFF4CAF50.toInt(), LocalDisplayColorMode.current))

@Composable
fun memorizationColor(): Color = Color(accentArgbFor(0xFFFFD700.toInt(), LocalDisplayColorMode.current))
