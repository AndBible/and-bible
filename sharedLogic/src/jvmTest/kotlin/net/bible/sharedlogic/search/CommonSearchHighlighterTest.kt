package net.bible.sharedlogic.search

import fi.sykero.kdom.Content
import fi.sykero.kdom.Element
import fi.sykero.kdom.Text
import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import kotlin.test.Test
import kotlin.test.assertEquals

/** Fixtures mirror the Android `SearchHighlighterTest` cases; expected values are the exact `StyledText` output. */
class CommonSearchHighlighterTest {
    private fun osisWith(vararg verseContent: Content): Element {
        val verse = Element("verse")
        verseContent.forEach { verse.addContent(it) }
        return Element("div").addContent(verse)
    }

    private fun lemmaWord(lemma: String, text: String) = Element("w").apply {
        setAttribute("lemma", lemma)
        addContent(Text(text))
    }

    @Test fun wordTermMatchProducesHighlightedRun() {
        val osis = osisWith(Text("In the beginning God created the heaven"))

        val result = CommonSearchHighlighter.highlight(osis, listOf("God"), emptyList())

        assertEquals(
            StyledText(listOf(
                StyledRun("In the beginning "),
                StyledRun("God", highlight = true),
                StyledRun(" created the heaven"),
            )),
            result,
        )
    }

    @Test fun strongsLemmaMatchProducesBoldRun() {
        val osis = osisWith(Text("In the beginning "), lemmaWord("strong:H0430", "God"), Text(" created"))

        val result = CommonSearchHighlighter.highlight(osis, emptyList(), listOf("strong:h0*430"))

        assertEquals(
            StyledText(listOf(
                StyledRun("In the beginning "),
                StyledRun("God", bold = true),
                StyledRun(" created"),
            )),
            result,
        )
    }

    @Test fun mixedBoldMatchesAndNonMatchingElementsStayPlain() {
        val osis = osisWith(
            lemmaWord("strong:H07225", "beginning"), Text(" "),
            lemmaWord("strong:H0430", "God"), Text(" "),
            lemmaWord("strong:H07225", "first"),
        )

        val result = CommonSearchHighlighter.highlight(osis, emptyList(), listOf("+strong:h0*7225"))

        assertEquals(
            StyledText(listOf(
                StyledRun("beginning", bold = true),
                StyledRun(" God "),
                StyledRun("first", bold = true),
            )),
            result,
        )
    }

    @Test fun multipleOccurrencesAndCaseInsensitiveMatch() {
        val osis = osisWith(Text("God said, and god saw"))

        val result = CommonSearchHighlighter.highlight(osis, listOf("god"), emptyList())

        assertEquals(
            StyledText(listOf(
                StyledRun("God", highlight = true),
                StyledRun(" said, and "),
                StyledRun("god", highlight = true),
                StyledRun(" saw"),
            )),
            result,
        )
    }

    @Test fun noMatchGivesSinglePlainRun() {
        val osis = osisWith(Text("In the beginning"))

        assertEquals(StyledText.plain("In the beginning"),
            CommonSearchHighlighter.highlight(osis, listOf("Moses"), emptyList()))
    }

    @Test fun notesAndReferencesAreExcluded() {
        val note = Element("note").addContent(Text("footnote"))
        val osis = osisWith(Text("Text "), note, Text("end"))

        assertEquals(StyledText.plain("Text end"),
            CommonSearchHighlighter.highlight(osis, listOf("footnote"), emptyList()))
    }

    @Test fun chineseTermIsHighlighted() {
        val osis = osisWith(Text("神爱世人上帝的话"))

        val result = CommonSearchHighlighter.highlight(osis, listOf("上帝"), emptyList())

        assertEquals(
            StyledText(listOf(StyledRun("神爱世人"), StyledRun("上帝", highlight = true), StyledRun("的话"))),
            result,
        )
    }

    // Literal expectations (derived from the input; Android's output is cross-checked in :app SearchHighlighterTest).

    @Test fun whitespaceRunsCollapseAndLeadingWhitespaceIsDropped() {
        val osis = osisWith(Text("  \n In  the\n\nbeginning   God"))

        val result = CommonSearchHighlighter.highlight(osis, listOf("God"), emptyList())

        assertEquals(
            StyledText(listOf(StyledRun("In the beginning "), StyledRun("God", highlight = true))),
            result,
        )
    }

    @Test fun whitespaceCollapsesAcrossBoldBoundaryInLemmaMode() {
        val osis = osisWith(Text("a  b\n"), lemmaWord("strong:H0430", "God  is"), Text("\n c"))

        val result = CommonSearchHighlighter.highlight(osis, emptyList(), listOf("strong:h0*430"))

        assertEquals(
            StyledText(listOf(StyledRun("a b "), StyledRun("God is", bold = true), StyledRun(" c"))),
            result,
        )
    }

    @Test fun nestedLemmaElementBoldsAllDescendants() {
        val inner = Element("seg").addContent(Text("inner")).addContent(Element("hi").addContent(Text(" deep")))
        val osis = osisWith(
            Text("start "),
            Element("w").apply { setAttribute("lemma", "strong:H0430"); addContent(inner); addContent(Text(" tail")) },
            Text(" end"),
        )

        val result = CommonSearchHighlighter.highlight(osis, emptyList(), listOf("strong:h0*430"))

        assertEquals(
            StyledText(listOf(StyledRun("start "), StyledRun("inner deep tail", bold = true), StyledRun(" end"))),
            result,
        )
    }

    @Test fun multipleTermsWithRepeatedMatches() {
        val osis = osisWith(Text("God said, and god saw that God was good"))

        val result = CommonSearchHighlighter.highlight(osis, listOf("god", "said"), emptyList())

        assertEquals(
            StyledText(listOf(
                StyledRun("God", highlight = true),
                StyledRun(" "),
                StyledRun("said", highlight = true),
                StyledRun(", and "),
                StyledRun("god", highlight = true),
                StyledRun(" saw that "),
                StyledRun("God", highlight = true),
                StyledRun(" was good"),
            )),
            result,
        )
    }
}
