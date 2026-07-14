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
package net.bible.android.control.search

import net.bible.android.TEST_SDK
import org.jdom2.Element
import org.jdom2.Text
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [TEST_SDK])
class SearchHighlighterTest {

    /** Build the `<div><verse>…</verse></div>` OSIS shape the highlighter walks. */
    private fun osisWith(vararg verseContent: org.jdom2.Content): Element {
        val verse = Element("verse")
        verseContent.forEach { verse.addContent(it) }
        return Element("div").addContent(verse)
    }

    @Test
    fun `word term match produces a highlighted run`() {
        val osis = osisWith(Text("In the beginning God created the heaven"))

        val result = SearchHighlighter.highlight(osis, terms = listOf("God"), lemmaTerms = emptyList())

        assertTrue("preview should contain verse text", result.plainText().contains("God"))
        val godRun = result.runs.first { it.text == "God" }
        assertTrue("matched word must be highlighted", godRun.highlight)
        assertFalse("word match must not be bolded (bold is reserved for lemma)", godRun.bold)
        // Non-matching text is neither highlighted nor bold.
        assertTrue(result.runs.any { it.text.contains("beginning") && !it.highlight && !it.bold })
    }

    @Test
    fun `strongs lemma match produces a bold run`() {
        val word = Element("w").apply {
            setAttribute("lemma", "strong:H0430")
            addContent(Text("God"))
        }
        val osis = osisWith(Text("In the beginning "), word, Text(" created"))

        val result = SearchHighlighter.highlight(
            osis,
            terms = emptyList(),
            lemmaTerms = listOf("strong:h0*430"),
        )

        assertTrue("preview should contain the lemma word", result.plainText().contains("God"))
        val godRun = result.runs.first { it.text.contains("God") }
        assertTrue("lemma-matched word must be bold", godRun.bold)
        assertFalse("lemma match must not set highlight", godRun.highlight)
    }
}
