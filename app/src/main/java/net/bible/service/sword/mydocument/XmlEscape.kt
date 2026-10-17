/*
 * Copyright (c) 2020-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
package net.bible.service.sword.mydocument

import androidx.annotation.VisibleForTesting

/**
 * Port of commons-text `StringEscapeUtils.escapeXml11` (kept byte-identical, pinned by `XmlEscapeParityTest`):
 * escapes `& < > " '`, drops U+0000, U+FFFE, U+FFFF and unpaired surrogates, and writes the control characters
 * XML 1.1 allows only as references (U+0001-U+0008, U+000B, U+000C, U+000E-U+001F, U+007F-U+0084,
 * U+0086-U+009F) as decimal numeric entities such as `&#1;`.
 */
@VisibleForTesting fun escapeXml11(input: String): String {
    val out = StringBuilder(input.length + 16)
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c == '"' -> out.append("&quot;")
            c == '&' -> out.append("&amp;")
            c == '\'' -> out.append("&apos;")
            c == '<' -> out.append("&lt;")
            c == '>' -> out.append("&gt;")
            c == '\u0000' || c == '\uFFFE' || c == '\uFFFF' -> Unit
            c.code in 0x1..0x8 || c.code in 0xB..0xC || c.code in 0xE..0x1F ||
                c.code in 0x7F..0x84 || c.code in 0x86..0x9F -> out.append("&#").append(c.code).append(';')
            c.isHighSurrogate() ->
                if (i + 1 < input.length && input[i + 1].isLowSurrogate()) {
                    out.append(c).append(input[i + 1])
                    i++
                }
            c.isLowSurrogate() -> Unit
            else -> out.append(c)
        }
        i++
    }
    return out.toString()
}
