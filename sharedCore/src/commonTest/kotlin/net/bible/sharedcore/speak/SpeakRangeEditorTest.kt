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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun v(ordinal: Int) = PickedVerse("Ps.23.$ordinal", "Ps 23:$ordinal", ordinal)

class SpeakRangeEditorTest {
    @Test fun empty_editor_cannot_commit_and_shows_no_error() {
        val e = SpeakRangeEditor()
        assertFalse(e.canCommit.value)
        assertFalse(e.showOrderError.value)
    }

    @Test fun one_endpoint_alone_cannot_commit_and_shows_no_error() {
        val e = SpeakRangeEditor()
        e.set(end = false, verse = v(1))
        assertFalse(e.canCommit.value)
        assertFalse(e.showOrderError.value)
    }

    @Test fun end_after_start_commits() {
        val e = SpeakRangeEditor()
        e.set(end = false, verse = v(1))
        e.set(end = true, verse = v(6))
        assertTrue(e.canCommit.value)
        assertFalse(e.showOrderError.value)
    }

    @Test fun equal_endpoints_are_not_commitable() {
        val e = SpeakRangeEditor()
        e.set(end = false, verse = v(3))
        e.set(end = true, verse = v(3))
        assertFalse(e.canCommit.value)
        assertTrue(e.showOrderError.value)
    }

    @Test fun end_before_start_shows_the_order_error() {
        val e = SpeakRangeEditor()
        e.set(end = false, verse = v(6))
        e.set(end = true, verse = v(1))
        assertFalse(e.canCommit.value)
        assertTrue(e.showOrderError.value)
    }

    @Test fun re_picking_the_start_clears_the_error_when_the_order_becomes_valid() {
        val e = SpeakRangeEditor()
        e.set(end = false, verse = v(6))
        e.set(end = true, verse = v(3))
        assertTrue(e.showOrderError.value)
        e.set(end = false, verse = v(1))
        assertTrue(e.canCommit.value)
        assertFalse(e.showOrderError.value)
    }

    @Test fun seed_loads_persisted_endpoints_and_clear_drops_them() {
        val e = SpeakRangeEditor()
        e.seed(v(1), v(6))
        assertEquals(v(1), e.start.value)
        assertTrue(e.canCommit.value)
        e.clearDraft()
        assertNull(e.start.value)
        assertNull(e.end.value)
        assertFalse(e.canCommit.value)
    }
}
