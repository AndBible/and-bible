package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentQueryMatchTest {
    private val kjv = listOf("KJV", "King James Version", "English", "CrossWire")

    @Test fun an_empty_or_blank_query_matches_everything() {
        assertTrue(matchesDocumentQuery("", kjv))
        assertTrue(matchesDocumentQuery("   ", kjv))
    }

    @Test fun a_single_character_matches_from_the_first_keystroke() {
        // The FTS path this replaces required three characters, so a two-letter abbreviation
        // could not be searched at all.
        assertTrue(matchesDocumentQuery("k", kjv))
        assertTrue(matchesDocumentQuery("KJ", kjv))
    }

    @Test fun every_term_must_match_something_and_order_does_not_matter() {
        assertTrue(matchesDocumentQuery("king james", kjv))
        assertTrue(matchesDocumentQuery("james king", kjv))
        assertTrue(matchesDocumentQuery("king english", kjv))   // terms may hit different fields
        assertFalse(matchesDocumentQuery("king martin", kjv))
    }

    @Test fun matching_is_a_token_PREFIX_not_a_substring() {
        assertTrue(matchesDocumentQuery("jam", kjv))
        assertFalse(matchesDocumentQuery("ames", kjv))
    }

    @Test fun punctuation_and_digits_separate_tokens_like_the_fts_tokenizer_did() {
        val fields = listOf("ESV2011", "English Standard Version (Anglicised)")
        assertTrue(matchesDocumentQuery("anglicised", fields))
        assertTrue(matchesDocumentQuery("esv", fields))
    }

    @Test fun case_folding_is_unicode_aware_not_ascii_only() {
        // FTS4's default tokenizer folds ASCII only, so this under-matched before.
        assertTrue(matchesDocumentQuery("äi", listOf("Äidinkieli")))
        assertTrue(matchesDocumentQuery("ÄI", listOf("äidinkieli")))
        assertTrue(matchesDocumentQuery("λό", listOf("Λόγος")))
    }

    @Test fun null_fields_are_skipped_rather_than_matching_or_throwing() {
        assertTrue(matchesDocumentQuery("kjv", listOf(null, "KJV")))
        assertFalse(matchesDocumentQuery("kjv", listOf(null, null)))
    }
}
