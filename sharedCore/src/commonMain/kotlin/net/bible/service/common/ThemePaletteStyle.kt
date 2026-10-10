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

package net.bible.service.common

/** Preference key of the app-wide theme style (see [ThemePaletteStyle]). */
const val THEME_PALETTE_STYLE_KEY = "theme_palette_style"

/**
 * How the app's Material 3 colour scheme is derived from the workspace colour, in
 * [DisplayColorMode.NORMAL] only (BW and COLOR_EINK ignore it). One entry per materialkolor
 * `PaletteStyle`; the mapping lives in `:sharedUi` (`AbColorScheme.kt`), the only module that
 * depends on materialkolor. Declaration order is the order shown in the settings list.
 */
enum class ThemePaletteStyle(val value: String) {
    FIDELITY("fidelity"),
    TONAL_SPOT("tonal_spot"),
    CONTENT("content"),
    NEUTRAL("neutral"),
    MONOCHROME("monochrome"),
    VIBRANT("vibrant"),
    EXPRESSIVE("expressive"),
    RAINBOW("rainbow"),
    FRUIT_SALAD("fruit_salad");

    companion object {
        val DEFAULT = FIDELITY

        /** Returns null for a null or unrecognized value so callers can apply [DEFAULT]. */
        fun fromValue(v: String?): ThemePaletteStyle? = entries.firstOrNull { it.value == v }
    }
}
