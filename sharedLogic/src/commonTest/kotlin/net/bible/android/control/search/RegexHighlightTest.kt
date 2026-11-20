package net.bible.android.control.search

import net.bible.sharedcore.search.StyledRun
import net.bible.sharedcore.search.StyledText
import kotlin.test.Test
import kotlin.test.assertEquals

class RegexHighlightTest {
    private val ic = RegexOption.IGNORE_CASE

    @Test fun highlightsTheMatchedSpan() {
        val out = highlightRegexMatches(StyledText.plain("the LORD God"), Regex("lord\\s+god", ic))
        assertEquals(listOf(StyledRun("the "), StyledRun("LORD God", highlight = true)), out.runs)
    }

    @Test fun matchSpanningRunsKeepsBold() {
        val text = StyledText(listOf(StyledRun("the "), StyledRun("LORD", bold = true), StyledRun(" God")))
        val out = highlightRegexMatches(text, Regex("lord god", ic))
        assertEquals(
            listOf(StyledRun("the "), StyledRun("LORD", bold = true, highlight = true), StyledRun(" God", highlight = true)),
            out.runs,
        )
    }

    @Test fun emptyMatchingPatternTerminatesAndLeavesTextUnchanged() {
        val text = StyledText.plain("abc")
        assertEquals(text, highlightRegexMatches(text, Regex("x*")))
    }

    @Test fun severalMatchesAndAnEmptyMatchingAlternative() {
        val out = highlightRegexMatches(StyledText.plain("a1b22"), Regex("\\d*"))
        assertEquals(listOf("a", "1", "b", "22"), out.runs.map { it.text })
        assertEquals(listOf(false, true, false, true), out.runs.map { it.highlight })
    }

    @Test fun noMatchReturnsTextUnchanged() {
        val text = StyledText.plain("abc")
        assertEquals(text, highlightRegexMatches(text, Regex("z")))
    }
}
