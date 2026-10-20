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
@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading.bridge

/**
 * The URL a "web search" of [query] opens. Android sends `Intent.ACTION_WEB_SEARCH` with the raw query, which the
 * default browser turns into a Google search; a platform without that intent opens the same search URL directly.
 * The query is percent-encoded as UTF-8 (unreserved characters stay literal).
 */
@kotlin.native.HiddenFromObjC
fun webSearchUrl(query: String): String = "https://www.google.com/search?q=" + percentEncode(query)

private fun percentEncode(text: String): String = buildString {
    for (b in text.encodeToByteArray()) {
        val c = b.toInt() and 0xFF
        val unreserved = c in 'A'.code..'Z'.code || c in 'a'.code..'z'.code || c in '0'.code..'9'.code ||
            c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
        if (unreserved) append(c.toChar()) else append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
    }
}

private const val HEX = "0123456789ABCDEF"
