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

package net.bible.android.view.compose

import net.bible.sharedui.bookmark.splitSelectionSample
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The split that makes a selection-style sample read as a SELECTION: a text-selection bookmark
 * covers part of a verse, a whole-verse bookmark covers all of it, so the selection sample is
 * decorated only up to here. One helper serves both call sites — the list tag (a single word, so it
 * splits mid-word) and the editor preview (a sentence, so it splits on a space).
 */
class SelectionSampleSplitTest {

    @Test
    fun a_tied_distance_splits_on_the_earlier_space() {
        // "For God so loved the world": the space before the middle and the space after it are
        // EQUALLY far from it (3 chars each) -- the algorithm is symmetric and only breaks ties
        // towards the earlier space, it does not search backward-first.
        assertEquals("For God so" to " loved the world", splitSelectionSample("For God so loved the world"))
    }

    @Test
    fun a_single_word_splits_mid_word() {
        assertEquals("High" to "light", splitSelectionSample("Highlight"))
    }

    @Test
    fun text_with_no_space_at_all_splits_mid_string() {
        // CJK has no word spaces; a character split is the honest fallback.
        assertEquals("強調" to "表示", splitSelectionSample("強調表示"))
    }

    @Test
    fun degenerate_inputs_are_left_whole() {
        assertEquals("" to "", splitSelectionSample(""))
        assertEquals("x" to "", splitSelectionSample("x"))
    }

    @Test
    fun a_leading_space_never_makes_the_decorated_part_empty() {
        val (decorated, rest) = splitSelectionSample(" a very long label name")
        assertEquals(" a very long", decorated)
        assertEquals(" label name", rest)
    }
}
