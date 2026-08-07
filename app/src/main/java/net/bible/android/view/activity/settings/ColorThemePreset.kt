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

package net.bible.android.view.activity.settings

import net.bible.android.activity.R
import net.bible.android.database.WorkspaceEntities

/**
 * Built-in named color palettes. Selecting one stamps its full day + night palette
 * onto a [WorkspaceEntities.Colors] and records [id] in [WorkspaceEntities.Colors.themeName].
 * Editing any individual color afterwards clears themeName back to Custom
 * (handled in ColorSettingsDataStore).
 */
enum class ColorThemePreset(
    val id: String,
    val labelRes: Int,
    val dayText: Int, val dayBg: Int, val dayLink: Int, val dayVerse: Int, val dayHeading: Int,
    val nightText: Int, val nightBg: Int, val nightLink: Int, val nightVerse: Int, val nightHeading: Int,
) {
    GRUVBOX("gruvbox", R.string.color_theme_gruvbox,
        0xFF3C3836.toInt(), 0xFFFBF1C7.toInt(), 0xFF076678.toInt(), 0xFF7C6F64.toInt(), 0xFFB57614.toInt(),
        0xFFEBDBB2.toInt(), 0xFF282828.toInt(), 0xFF83A598.toInt(), 0xFFA89984.toInt(), 0xFFFABD2F.toInt()),
    NORD("nord", R.string.color_theme_nord,
        0xFF2E3440.toInt(), 0xFFECEFF4.toInt(), 0xFF5E81AC.toInt(), 0xFF4C566A.toInt(), 0xFFB48EAD.toInt(),
        0xFFD8DEE9.toInt(), 0xFF2E3440.toInt(), 0xFF88C0D0.toInt(), 0xFF7B88A1.toInt(), 0xFF81A1C1.toInt()),
    SOLARIZED("solarized", R.string.color_theme_solarized,
        0xFF657B83.toInt(), 0xFFFDF6E3.toInt(), 0xFF268BD2.toInt(), 0xFF93A1A1.toInt(), 0xFFB58900.toInt(),
        0xFF839496.toInt(), 0xFF002B36.toInt(), 0xFF268BD2.toInt(), 0xFF586E75.toInt(), 0xFFB58900.toInt()),
    DRACULA("dracula", R.string.color_theme_dracula,
        0xFF1F1F1F.toInt(), 0xFFF8F8F2.toInt(), 0xFF036A96.toInt(), 0xFF6272A4.toInt(), 0xFF644AC9.toInt(),
        0xFFF8F8F2.toInt(), 0xFF282A36.toInt(), 0xFF8BE9FD.toInt(), 0xFF6272A4.toInt(), 0xFFBD93F9.toInt()),
    SEPIA("sepia", R.string.color_theme_sepia,
        0xFF5B4636.toInt(), 0xFFF4ECD8.toInt(), 0xFF8B5A2B.toInt(), 0xFFA1887F.toInt(), 0xFF7B4F2C.toInt(),
        0xFFD8C8B0.toInt(), 0xFF2B2622.toInt(), 0xFFC9A066.toInt(), 0xFF8A7A68.toInt(), 0xFFD9B382.toInt());

    fun applyTo(c: WorkspaceEntities.Colors) {
        c.dayTextColor = dayText
        c.dayBackground = dayBg
        c.dayLinkColor = dayLink
        c.dayVerseNumberColor = dayVerse
        c.dayHeadingColor = dayHeading
        c.nightTextColor = nightText
        c.nightBackground = nightBg
        c.nightLinkColor = nightLink
        c.nightVerseNumberColor = nightVerse
        c.nightHeadingColor = nightHeading
        c.themeName = id
    }

    companion object {
        fun byId(id: String?): ColorThemePreset? =
            if (id.isNullOrEmpty()) null else entries.find { it.id == id }
    }
}
