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
package net.bible.sharedlogic.markdown

/**
 * Post-processing of generated HTML into XHTML that is safe for the OSIS/XML pipeline, the counterpart of
 * `ensureXhtmlCompatible` in `MarkdownToXhtml` (`:app`). Pure string work so it is tested on the JVM although the
 * Markdown parser feeding it (JetBrains markdown) only exists on iOS.
 *
 * Steps: strip the `<body>` wrapper the JetBrains generator adds; make void elements self-closing; keep the five XML
 * built-in entities and numeric references; replace known HTML named entities by their characters. Deviation from
 * Android (which resolves every HTML4 entity through commons-text): an unknown named entity is escaped (`&amp;name;`)
 * so the result is always well-formed XML instead of failing the parse later.
 */
object HtmlToXhtml {
    private val xmlBuiltIn = setOf("amp", "lt", "gt", "quot", "apos")
    private val entityRegex = Regex("&([a-zA-Z][a-zA-Z0-9]*);")
    private val voidRegex = Regex("<(br|hr|img|input|wbr|col)\\b([^<>]*?)\\s*/?>", RegexOption.IGNORE_CASE)
    private val bodyRegex = Regex("^\\s*<body\\b[^>]*>(.*)</body>\\s*$", RegexOption.DOT_MATCHES_ALL)

    /** Named HTML entities that commonly appear in Markdown prose; [xmlBuiltIn] are kept as is. */
    private val named: Map<String, String> = mapOf(
        "nbsp" to " ", "copy" to "©", "reg" to "®", "trade" to "™", "mdash" to "—",
        "ndash" to "–", "hellip" to "…", "laquo" to "«", "raquo" to "»", "lsquo" to "‘",
        "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•", "middot" to "·",
        "deg" to "°", "plusmn" to "±", "times" to "×", "divide" to "÷", "euro" to "€",
        "pound" to "£", "yen" to "¥", "cent" to "¢", "sect" to "§", "para" to "¶",
        "larr" to "←", "rarr" to "→", "uarr" to "↑", "darr" to "↓", "hearts" to "♥",
        "frac12" to "½", "frac14" to "¼", "frac34" to "¾", "shy" to "­", "ensp" to " ",
        "emsp" to " ", "thinsp" to " ", "dagger" to "†", "Dagger" to "‡", "permil" to "‰",
        "iexcl" to "¡", "iquest" to "¿", "szlig" to "ß", "auml" to "ä", "ouml" to "ö",
        "uuml" to "ü", "Auml" to "Ä", "Ouml" to "Ö", "Uuml" to "Ü", "aring" to "å",
        "Aring" to "Å", "eacute" to "é", "egrave" to "è", "aacute" to "á", "agrave" to "à",
    )

    fun toXhtml(html: String): String {
        val unwrapped = bodyRegex.matchEntire(html)?.groupValues?.get(1) ?: html
        val selfClosed = voidRegex.replace(unwrapped) { "<${it.groupValues[1]}${it.groupValues[2]} />" }
        return entityRegex.replace(selfClosed) { m ->
            val name = m.groupValues[1]
            when {
                name in xmlBuiltIn -> m.value
                else -> named[name] ?: "&amp;$name;"
            }
        }
    }
}
