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

package net.bible.sharedcore.speak

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeakSheetStackTest {
    @Test fun starts_closed() {
        val s = SpeakSheetStack()
        assertNull(s.current)
        assertEquals(0, s.depth)
    }

    @Test fun open_discards_a_previous_stack() {
        val s = SpeakSheetStack()
        s.open(SpeakSheetPage.Settings)
        s.push(SpeakSheetPage.RepeatRange)
        s.open(SpeakSheetPage.Settings)
        assertEquals(listOf(SpeakSheetPage.Settings), s.pages.value)
    }

    @Test fun pop_walks_back_one_page_and_closes_at_depth_one() {
        val s = SpeakSheetStack()
        s.open(SpeakSheetPage.Settings)
        s.push(SpeakSheetPage.RepeatRange)
        s.push(SpeakSheetPage.PickVerse(end = false))
        s.pop(); assertEquals(SpeakSheetPage.RepeatRange, s.current)
        s.pop(); assertEquals(SpeakSheetPage.Settings, s.current)
        s.pop(); assertNull(s.current)
        s.pop() // no-op when already closed
        assertEquals(0, s.depth)
    }

    @Test fun pick_verse_pages_are_distinct_by_endpoint() {
        val s = SpeakSheetStack()
        s.open(SpeakSheetPage.PickVerse(end = false))
        assertTrue(s.current != SpeakSheetPage.PickVerse(end = true))
    }

    @Test fun close_if_empties_the_whole_stack() {
        val s = SpeakSheetStack()
        s.open(SpeakSheetPage.Settings)
        s.push(SpeakSheetPage.SleepTimer)
        s.closeIf { it is SpeakSheetPage.Settings }
        assertEquals(0, s.depth)
    }
}
