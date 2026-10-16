/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.database

/** Platform-independent replacements for the [android.graphics.Color] helpers used by stored colour ints. */

fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
    (alpha shl 24) or (red shl 16) or (green shl 8) or blue

fun Int.alphaOf(): Int = this ushr 24
fun Int.redOf(): Int = (this shr 16) and 0xFF
fun Int.greenOf(): Int = (this shr 8) and 0xFF
fun Int.blueOf(): Int = this and 0xFF

/**
 * Parses `#RRGGBB` (alpha 0xFF) or `#AARRGGBB`, case-insensitive.
 * @throws IllegalArgumentException for any other input.
 */
fun parseArgbHex(s: String): Int {
    require(s.startsWith("#") && (s.length == 7 || s.length == 9)) { "Unknown color: $s" }
    val digits = s.substring(1)
    require(digits.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "Unknown color: $s" }
    val value = digits.toLong(16).toInt()
    return if (digits.length == 6) value or (0xFF shl 24) else value
}
