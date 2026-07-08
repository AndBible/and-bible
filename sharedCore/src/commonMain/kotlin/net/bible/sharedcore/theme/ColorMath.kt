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

package net.bible.sharedcore.theme

import net.bible.service.common.DisplayColorMode

/** Luminance-weighted grayscale of an ARGB int, alpha preserved. */
fun toGrayscaleArgb(argb: Int): Int {
    val a = (argb ushr 24) and 0xFF
    val r = (argb shr 16) and 0xFF
    val g = (argb shr 8) and 0xFF
    val b = argb and 0xFF
    val y = ((r * 299 + g * 587 + b * 114) / 1000).coerceIn(0, 255)
    return (a shl 24) or (y shl 16) or (y shl 8) or y
}

/** Accent color for a mode: colored in NORMAL/COLOR_EINK, grayscale in BW. */
fun accentArgbFor(baseArgb: Int, mode: DisplayColorMode): Int =
    if (mode == DisplayColorMode.BW) toGrayscaleArgb(baseArgb) else baseArgb
