package net.bible.sharedcore.search

import kotlin.test.Test
import kotlin.test.assertEquals

class EpubSearchModelsTest {
    @Test fun adjustSearchText_phrase_is_quoted() {
        assertEquals("\"in the beginning\"", adjustSearchText(EpubSearchMode.PHRASE, "in the beginning"))
    }
    @Test fun adjustSearchText_allWords_joins_with_AND() {
        assertEquals("god AND said", adjustSearchText(EpubSearchMode.ALL_WORDS, "god said"))
    }
    @Test fun adjustSearchText_anyWord_joins_with_OR() {
        assertEquals("god OR said", adjustSearchText(EpubSearchMode.ANY_WORD, "god said"))
    }
    @Test fun adjustSearchText_fts_is_raw() {
        assertEquals("god NEAR(said)", adjustSearchText(EpubSearchMode.FTS, "god NEAR(said)"))
    }

    @Test fun parseHighlight_splits_bold_runs() {
        val st = parseHighlightHtml("a <b>hit</b> z")
        assertEquals(
            listOf(
                StyledRun("a ", bold = false),
                StyledRun("hit", bold = true),
                StyledRun(" z", bold = false),
            ),
            st.runs,
        )
    }
    @Test fun parseHighlight_plain_text_is_single_run() {
        assertEquals(listOf(StyledRun("nothing bold")), parseHighlightHtml("nothing bold").runs)
    }
    @Test fun parseHighlight_unescapes_entities() {
        assertEquals(listOf(StyledRun("a & b")), parseHighlightHtml("a &amp; b").runs)
    }

    @Test
    fun collapsesRunsOfWhitespaceInsideOneRun() {
        val st = parseHighlightHtml("When we speak of \n     “Christ being in us,”")
        assertEquals("When we speak of “Christ being in us,”", st.plainText())
    }

    @Test
    fun collapsesWhitespaceAcrossAStyledRunBoundary() {
        // The space before <b> and the newline after it are ONE gap, split over two runs.
        val st = parseHighlightHtml("say \n<b>peace</b>\n  now")
        assertEquals(
            listOf(StyledRun("say ", false), StyledRun("peace", true), StyledRun(" now", false)),
            st.runs,
        )
    }

    @Test
    fun trimsLeadingAndTrailingWhitespace() {
        val st = parseHighlightHtml("\n   say <b>peace</b>   \n")
        assertEquals(listOf(StyledRun("say ", false), StyledRun("peace", true)), st.runs)
    }

    @Test
    fun keepsNonBreakingSpace() {
        // NBSP is content, not layout: an EPUB uses it to hold a reference together. Only the ASCII
        // run collapses; the two NBSPs come through exactly as they are.
        val st = parseHighlightHtml("Gen\u00A01:1   and\u00A0\u00A0more")
        assertEquals("Gen\u00A01:1 and\u00A0\u00A0more", st.plainText())
    }

    @Test
    fun emptyInputStillYieldsOneEmptyRun() {
        assertEquals(listOf(StyledRun("")), parseHighlightHtml("   \n  ").runs)
    }
}
