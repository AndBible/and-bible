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

package net.bible.service.sword.mydocument

import org.apache.commons.text.StringEscapeUtils
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Converts Markdown content to XHTML-compatible HTML using commonmark-java.
 *
 * The output is suitable for embedding in the OSIS/XML pipeline:
 * - Void elements are self-closing (`<br />`, `<hr />`, `<img ... />`)
 * - HTML named entities are converted to Unicode characters
 * - Only the 5 XML built-in entities are preserved (`&amp;`, `&lt;`, `&gt;`, `&quot;`, `&apos;`)
 */
object MarkdownToXhtml {
    private val extensions = listOf(TablesExtension.create())
    private val parser = Parser.builder().extensions(extensions).build()
    private val renderer = HtmlRenderer.builder().extensions(extensions).build()

    fun convert(markdown: String): String {
        val document = parser.parse(markdown)
        val html = renderer.render(document)
        return ensureXhtmlCompatible(html)
    }

    private val xmlBuiltInEntities = setOf("amp", "lt", "gt", "quot", "apos")
    private val htmlEntityRegex = Regex("&([a-zA-Z][a-zA-Z0-9]*);")

    /**
     * Post-process HTML to ensure XHTML compatibility.
     *
     * commonmark-java already outputs self-closing void elements, so we mainly
     * need to handle HTML named entities that aren't valid in XML. The 5 XML
     * built-in entities (&amp;, &lt;, &gt;, &quot;, &apos;) are kept as-is.
     * Other named HTML entities (e.g. &nbsp;, &mdash;) are replaced with their
     * Unicode characters. Numeric character references (&#123;, &#x1F600;) are
     * valid in both HTML and XML so they're left untouched.
     */
    private fun ensureXhtmlCompatible(html: String): String {
        return htmlEntityRegex.replace(html) { match ->
            val entityName = match.groupValues[1]
            if (entityName in xmlBuiltInEntities) {
                match.value
            } else {
                val unescaped = StringEscapeUtils.unescapeHtml4(match.value)
                if (unescaped != match.value) unescaped else match.value
            }
        }
    }
}
