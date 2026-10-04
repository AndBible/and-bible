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

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/** ARGB `Int` <-> Compose [Color] bridge, plus the label/bookmark preset palette. Kept in
 *  `commonMain`: everything here is plain [Color]/[Int] math, no `android.graphics.*` — safe for
 *  the iOS target.
 *
 *  NOTE (batch 4c): the picker no longer offers this palette — `AbColorPickerDialog` uses classic's
 *  Material presets (`net.bible.sharedcore.theme.MATERIAL_PRESETS`). What remains of this list is
 *  the ARGB<->Color bridge, used by five screens, and fixture data for four golden tests.
 *
 *  The palette mirrors `BookmarkStyle`'s colours in
 *  `app/src/main/java/net/bible/android/database/bookmarks/BookmarkEntities.kt` (each built via
 *  `Color.argb(255, r, g, b)`), minus `SPEAK` (that entry is a hard-coded internal style for Speak
 *  bookmarks, explicitly excluded from user-facing style lists) and with the `YELLOW_STAR`/
 *  `YELLOW_HIGHLIGHT` duplicate collapsed to one swatch. `BLUE_HIGHLIGHT` (== `defaultLabelColor`)
 *  is included. */
object AbColor {
    val palette: List<Int> = listOf(
        0xFFFFFF00.toInt(), // YELLOW_STAR / YELLOW_HIGHLIGHT
        0xFFD50000.toInt(), // RED_HIGHLIGHT
        0xFF00FF00.toInt(), // GREEN_HIGHLIGHT
        0xFF91A7FF.toInt(), // BLUE_HIGHLIGHT (defaultLabelColor)
        0xFFFFA500.toInt(), // ORANGE_HIGHLIGHT
        0xFF800080.toInt(), // PURPLE_HIGHLIGHT
        0xFF806380.toInt(), // UNDERLINE
    )

    fun toComposeColor(argb: Int): Color = Color(argb)
    fun toArgb(c: Color): Int = c.toArgb()
}
