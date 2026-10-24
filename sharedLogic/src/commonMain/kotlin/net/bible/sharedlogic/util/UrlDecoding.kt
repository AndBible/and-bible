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

package net.bible.sharedlogic.util

/**
 * Common equivalent of `java.net.URLDecoder.decode(s, "UTF-8")`: `+` becomes a space, runs of `%XX` are decoded
 * as UTF-8 bytes (malformed bytes become U+FFFD), and a `%` not followed by two hex digits throws
 * [IllegalArgumentException].
 */
fun decodeUrl(s: String): String {
    if (s.indexOf('%') < 0 && s.indexOf('+') < 0) return s
    val out = StringBuilder(s.length)
    var bytes = ByteArray(0)
    var count = 0
    fun flush() {
        if (count > 0) {
            out.append(bytes.decodeToString(0, count))
            count = 0
        }
    }
    var i = 0
    while (i < s.length) {
        val c = s[i]
        if (c == '%') {
            if (i + 2 >= s.length) {
                throw IllegalArgumentException("Incomplete trailing escape (%) pattern")
            }
            val hi = hexValue(s[i + 1])
            val lo = hexValue(s[i + 2])
            if (hi == null || lo == null) throw IllegalArgumentException("Illegal hex characters in escape (%) pattern")
            if (count == bytes.size) bytes = bytes.copyOf(maxOf(16, bytes.size * 2))
            bytes[count++] = (hi * 16 + lo).toByte()
            i += 3
        } else {
            flush()
            out.append(if (c == '+') ' ' else c)
            i++
        }
    }
    flush()
    return out.toString()
}

private fun hexValue(c: Char): Int? = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> null
}
