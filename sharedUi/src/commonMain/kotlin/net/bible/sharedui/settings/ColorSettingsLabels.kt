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

/** Host-resolved strings for the colours editor (translated text stays in strings.xml). */
data class ColorSettingsLabels(
    val dayMode: String, // R.string.colors_day_mode_title
    val nightMode: String, // R.string.colors_night_mode_title
    val textColor: String, // R.string.color_text
    val backgroundColor: String, // R.string.color_background
    val noise: String, // R.string.prefs_noise_title
    val workspaceColor: String, // R.string.color_workspace
    val backgroundImageDay: String, // R.string.background_image_day
    val backgroundImageNight: String, // R.string.background_image_night
    val opacityDay: String, // R.string.background_image_opacity_day
    val opacityNight: String, // R.string.background_image_opacity_night
    val change: String, // R.string.background_image_change (new)
    val reset: String, // R.string.reset
) {
    companion object {
        fun forTest() = ColorSettingsLabels(
            dayMode = "Day mode",
            nightMode = "Night mode",
            textColor = "Text",
            backgroundColor = "Background",
            noise = "Noise",
            workspaceColor = "Workspace color",
            backgroundImageDay = "Background image (day)",
            backgroundImageNight = "Background image (night)",
            opacityDay = "Opacity (day)",
            opacityNight = "Opacity (night)",
            change = "Change",
            reset = "Reset",
        )
    }
}
