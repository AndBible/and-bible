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
}
