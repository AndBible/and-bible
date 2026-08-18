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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnySelectedDeletableTest {
    private fun row(id: String, canDelete: Boolean) = DocRow(
        docId = id,
        osisId = id,
        abbreviation = id,
        name = "Doc $id",
        language = LangOption("en", "English", "en"),
        repository = "CrossWire",
        category = DocCategory.BIBLE,
        installStatus = DocInstallStatus.INSTALLED,
        percentDone = 0,
        recommended = false,
        badWarn = false,
        locked = false,
        enciphered = false,
        canDelete = canDelete,
        installSizeMb = null,
    )

    private val rows = listOf(row("A", true), row("B", false), row("C", true))

    @Test
    fun an_empty_selection_offers_no_delete() {
        assertFalse(anySelectedDeletable(rows, emptySet()))
    }

    @Test
    fun one_deletable_row_is_enough() {
        assertTrue(anySelectedDeletable(rows, setOf("A")))
    }

    @Test
    fun a_mixed_selection_offers_delete() {
        assertTrue(anySelectedDeletable(rows, setOf("B", "C")))
    }

    @Test
    fun a_selection_of_only_undeletable_rows_offers_no_delete() {
        assertFalse(anySelectedDeletable(rows, setOf("B")))
    }

    @Test
    fun ids_that_are_not_in_the_row_list_are_ignored() {
        assertFalse(anySelectedDeletable(rows, setOf("Z")))
    }

    @Test
    fun the_order_of_the_selection_does_not_matter() {
        // The old host rule read only the FIRST selected row, so B-then-C hid the action
        // while C-then-B showed it. Both must now offer it.
        assertTrue(anySelectedDeletable(rows, setOf("B", "C")))
        assertTrue(anySelectedDeletable(rows, setOf("C", "B")))
    }
}
