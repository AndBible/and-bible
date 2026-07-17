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
package net.bible.sharedcore.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class PickerFilterTest {
    private val langs = listOf(
        LangOption("en", "English", "en"),
        LangOption("grc", "Greek", "grc"),
        LangOption("de", "German", "de"),
        LangOption("fr", "French", "fr"),
    )
    private val label: (LangOption) -> String = { it.displayName }

    @Test fun blank_query_returns_all_unchanged() {
        assertEquals(langs, filterPickerOptions(langs, "", label))
        assertEquals(langs, filterPickerOptions(langs, "   ", label))
    }

    @Test fun filters_case_insensitively_by_label() {
        assertEquals(listOf(langs[1]), filterPickerOptions(langs, "greek", label))
        assertEquals(listOf(langs[1]), filterPickerOptions(langs, "GREEK", label))
        assertEquals(listOf(langs[1]), filterPickerOptions(langs, "GrEe", label))
    }

    @Test fun matches_as_substring_contains_not_prefix() {
        // "en" appears inside "French" (fr**en**ch) and "English" → both kept.
        assertEquals(listOf(langs[0], langs[3]), filterPickerOptions(langs, "en", label))
    }

    @Test fun trims_surrounding_whitespace_before_matching() {
        assertEquals(listOf(langs[2]), filterPickerOptions(langs, "  german  ", label))
    }

    @Test fun no_match_returns_empty() {
        assertEquals(emptyList(), filterPickerOptions(langs, "xyz", label))
    }

    @Test fun preserves_relative_order() {
        // Query "r" matches Greek, German, French (in original order).
        assertEquals(listOf(langs[1], langs[2], langs[3]), filterPickerOptions(langs, "r", label))
    }

    @Test fun works_with_nullable_option_via_label_mapping() {
        // Mirrors the language filter's null = "All" sentinel.
        val opts: List<LangOption?> = listOf(null) + langs
        val nullableLabel: (LangOption?) -> String = { it?.displayName ?: "All" }
        assertEquals(listOf<LangOption?>(null), filterPickerOptions(opts, "all", nullableLabel))
    }
}
