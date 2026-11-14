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

package net.bible.android.control.search

import org.crosswire.jsword.index.search.SearchType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The search type's word decoration keeps a bracketed term range as one word (#4093): split into words, "All words"
 * turned `[grace TO hope]` into `+[grace +TO +hope]`, which no longer reads as a range, so the search failed silently
 * instead of reporting the unsupported syntax.
 */
class SearchTypeDecorationTest {
    @Test fun aRangeIsDecoratedAsOneWord() {
        val cases = listOf(
            Triple(SearchType.ALL_WORDS, "[grace TO hope]", "+[grace TO hope]"),
            Triple(SearchType.ALL_WORDS, "{grace TO hope}", "+{grace TO hope}"),
            Triple(SearchType.ALL_WORDS, "faith [a TO b] hope", "+faith +[a TO b] +hope"),
            Triple(SearchType.ALL_WORDS, "[a TO b] {c TO d}", "+[a TO b] +{c TO d}"),
            Triple(SearchType.ANY_WORDS, "[grace TO hope]", "[grace TO hope]"),
            Triple(SearchType.PHRASE, "[grace TO hope]", "\"[grace TO hope]\""),
            // unchanged: plain words, and an unclosed bracket is not a group
            Triple(SearchType.ALL_WORDS, "in the beginning", "+in +the +beginning"),
            Triple(SearchType.ALL_WORDS, "grace^2", "+grace^2"),
            Triple(SearchType.ALL_WORDS, "[grace TO", "+[grace +TO"),
            Triple(SearchType.ANY_WORDS, "light", "light"),
        )
        for ((type, text, expected) in cases) {
            assertEquals(expected, decorateSearchWords(text, type), "$type '$text'")
        }
    }
}
