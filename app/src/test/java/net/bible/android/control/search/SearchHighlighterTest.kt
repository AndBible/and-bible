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
import net.bible.sharedlogic.search.CommonSearchHighlighter
import fi.sykero.kdom.Content
import fi.sykero.kdom.Element
import fi.sykero.kdom.Text
import org.junit.Assert.assertEquals
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
    private fun osisWith(vararg verseContent: Content): Element {
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

    @Test
    fun allWordsStrongsSearchBoldsWithoutBlankPreview() {
        val word = Element("w").apply {
            setAttribute("lemma", "strong:H07225")
            addContent(Text("God"))
        }
        val osis = osisWith(Text("In the beginning "), word, Text(" created"))

        val result = SearchHighlighter.highlight(osis, terms = emptyList(), lemmaTerms = listOf("+strong:h0*7225"))

        assertTrue("preview must not be blank", result.plainText().isNotEmpty())
        assertTrue("lemma-matched word must be bold", result.runs.first { it.text.contains("God") }.bold)
    }

    @Test
    fun chineseTermIsHighlighted() {
        val osis = osisWith(Text("神爱世人上帝的话"))

        val result = SearchHighlighter.highlight(osis, terms = listOf("上帝"), lemmaTerms = emptyList())

        assertTrue(result.runs.any { it.text == "上帝" && it.highlight })
    }

    @Test
    fun thaiTermIsHighlighted() {
        val osis = osisWith(Text("ในปฐมกาลพระเจ้าทรงสร้าง"))

        val result = SearchHighlighter.highlight(osis, terms = listOf("พระเจ้า"), lemmaTerms = emptyList())

        assertTrue(result.runs.any { it.text == "พระเจ้า" && it.highlight })
    }

    private fun assertParity(osis: Element, terms: List<String>, lemmaTerms: List<String>) {
        assertEquals(
            SearchHighlighter.highlight(osis, terms, lemmaTerms),
            CommonSearchHighlighter.highlight(osis, terms, lemmaTerms),
        )
    }

    private fun lemmaWord(lemma: String, vararg content: Content) = Element("w").apply {
        setAttribute("lemma", lemma)
        content.forEach { addContent(it) }
    }

    @Test
    fun commonParity_lemmaAndSingleTerm() {
        val lemmaOsis = osisWith(Text("In the beginning "), lemmaWord("strong:H0430", Text("God")), Text(" created"))
        assertParity(lemmaOsis, emptyList(), listOf("strong:h0*430"))
        assertParity(osisWith(Text("In the beginning God created the heaven")), listOf("God"), emptyList())
    }

    @Test
    fun commonParity_multipleSpacesAndNewlines() {
        assertParity(osisWith(Text("In  the\n\nbeginning   God\tcreated")), listOf("God", "the"), emptyList())
        assertParity(
            osisWith(Text("a  b\n"), lemmaWord("strong:H0430", Text("God  is")), Text("\n c")),
            emptyList(), listOf("strong:h0*430"),
        )
    }

    @Test
    fun commonParity_leadingWhitespaceAndLiteralExpectation() {
        val osis = osisWith(Text("  \n In  the\n\nbeginning   God"))
        assertParity(osis, listOf("God"), emptyList())
        assertEquals(
            net.bible.sharedcore.search.StyledText(listOf(
                net.bible.sharedcore.search.StyledRun("In the beginning "),
                net.bible.sharedcore.search.StyledRun("God", highlight = true),
            )),
            SearchHighlighter.highlight(osis, listOf("God"), emptyList()),
        )
    }

    @Test
    fun commonParity_nestedLemmaElement() {
        val inner = Element("seg").addContent(Text("inner")).addContent(Element("hi").addContent(Text(" deep")))
        val osis = osisWith(Text("start "), lemmaWord("strong:H0430", inner, Text(" tail")), Text(" end"))
        assertParity(osis, emptyList(), listOf("strong:h0*430"))
    }

    @Test
    fun commonParity_multipleTermMatches() {
        assertParity(osisWith(Text("God said, and god saw that God was good")), listOf("god", "said"), emptyList())
        assertParity(osisWith(Text("light and lighter lights")), listOf("light"), emptyList())
    }

    @Test
    fun commonParity_noMatch() {
        assertParity(osisWith(Text("In the beginning")), listOf("Moses"), emptyList())
    }

    /** Intentional divergence: Android runs the verse through Html.fromHtml, Common treats kdom text as already decoded. */
    @Test
    fun commonKeepsEntitiesVerbatimWhereAndroidDecodesThem() {
        val osis = osisWith(Text("Tom &amp; Jerry"))
        assertEquals("Tom & Jerry", SearchHighlighter.highlight(osis, emptyList(), emptyList()).plainText())
        assertEquals("Tom &amp; Jerry", CommonSearchHighlighter.highlight(osis, emptyList(), emptyList()).plainText())
    }
}
