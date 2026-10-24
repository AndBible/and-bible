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

import net.bible.sharedlogic.text.HtmlEntities

/**
 * Post-processing of generated HTML into XHTML that is safe for the OSIS/XML pipeline, the counterpart of
 * `ensureXhtmlCompatible` in `MarkdownToXhtml` (`:app`). Pure string work so it is tested on the JVM although the
 * Markdown parser feeding it (JetBrains markdown) only exists on iOS.
 *
 * Steps: strip the `<body>` wrapper the JetBrains generator adds; make void elements self-closing; keep the five XML
 * built-in entities and numeric references; replace every HTML4 named entity by its character (the common `HtmlEntities` table, a port of
 * commons-text `unescapeHtml4`, as Android does). Deviation: a name that is not an HTML4 entity is escaped
 * (`&amp;name;`) so the result is always well-formed XML instead of failing the parse later.
 */
object HtmlToXhtml {
    private val xmlBuiltIn = setOf("amp", "lt", "gt", "quot", "apos")
    private val entityRegex = Regex("&([a-zA-Z][a-zA-Z0-9]*);")
    private val voidRegex = Regex("<(br|hr|img|input|wbr|col)\\b([^<>]*?)\\s*/?>", RegexOption.IGNORE_CASE)
    private val bodyRegex = Regex("^\\s*<body\\b[^>]*>(.*)</body>\\s*$", RegexOption.DOT_MATCHES_ALL)

    fun toXhtml(html: String): String {
        val unwrapped = bodyRegex.matchEntire(html)?.groupValues?.get(1) ?: html
        val selfClosed = voidRegex.replace(unwrapped) { "<${it.groupValues[1]}${it.groupValues[2]} />" }
        return entityRegex.replace(selfClosed) { m ->
            val name = m.groupValues[1]
            when {
                name in xmlBuiltIn -> m.value
                else -> HtmlEntities.unescapeHtml4(m.value).takeIf { it != m.value } ?: "&amp;$name;"
            }
        }
    }
}
