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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Proves [MarkdownToXhtml.convert] is byte-for-byte identical to the commonmark-java implementation
 * it was written against (copied below as the oracle) for every fixture in `markdown-parity/`.
 */
class MarkdownToXhtmlParityTest {

    /** The pre-L1b implementation, verbatim, kept as the oracle. */
    private object Oracle {
        private val extensions = listOf(TablesExtension.create())
        private val parser = Parser.builder().extensions(extensions).build()
        private val renderer = HtmlRenderer.builder().extensions(extensions).build()
        private val xmlBuiltInEntities = setOf("amp", "lt", "gt", "quot", "apos")
        private val htmlEntityRegex = Regex("&([a-zA-Z][a-zA-Z0-9]*);")

        fun convert(markdown: String): String {
            val html = renderer.render(parser.parse(markdown))
            return htmlEntityRegex.replace(html) { match ->
                val name = match.groupValues[1]
                if (name in xmlBuiltInEntities) match.value else {
                    val unescaped = StringEscapeUtils.unescapeHtml4(match.value)
                    if (unescaped != match.value) unescaped else match.value
                }
            }
        }
    }

    private fun fixtures(): List<File> {
        val dir = listOf("src/test/resources/markdown-parity", "app/src/test/resources/markdown-parity")
            .map { File(it) }.first { it.isDirectory }
        return dir.listFiles { f -> f.extension == "md" }!!.sortedBy { it.name }
    }

    @Test
    fun everyFixtureRendersIdentically() {
        val files = fixtures()
        assertTrue("expected at least 10 fixtures, got ${files.size}", files.size >= 10)
        for (f in files) {
            val md = f.readText()
            assertEquals("fixture ${f.name}", Oracle.convert(md), MarkdownToXhtml.convert(md))
        }
    }

    @Test
    fun hardBreakStaysXhtml() {
        val out = MarkdownToXhtml.convert("a  \nb")
        assertTrue(out, out.contains("<br />"))
        assertTrue(out, !out.contains("<br>"))
    }

    @Test
    fun namedEntitiesBecomeUnicodeButXmlOnesStay() {
        val out = MarkdownToXhtml.convert("A&nbsp;B &mdash; &amp; &lt;")
        assertTrue(out, out.contains("A B — &amp; &lt;"))
    }
}
