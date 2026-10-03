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

/**
 * Display color treatment for the app.
 *
 * - [NORMAL]: full color.
 * - [BW]: monochrome (black & white) — the whole UI is grayscale.
 * - [COLOR_EINK]: monochrome base but selected accents (bookmark colors, active-window
 *   indicator, scroll helper lines, top-margin line) are shown in color. Intended for
 *   color e-ink screens where high contrast still matters.
 */
enum class DisplayColorMode(val value: String) {
    NORMAL("normal"),
    BW("bw"),
    COLOR_EINK("color_eink");

    companion object {
        /** Returns null for a null or unrecognized value so callers can apply a device default. */
        fun fromValue(v: String?): DisplayColorMode? = values().firstOrNull { it.value == v }
    }
}
