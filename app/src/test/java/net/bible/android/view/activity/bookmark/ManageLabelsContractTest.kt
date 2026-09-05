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

package net.bible.android.view.activity.bookmark

import net.bible.android.database.IdType
import net.bible.android.view.activity.bookmark.ManageLabelsContract.ManageLabelsData
import net.bible.android.view.activity.bookmark.ManageLabelsContract.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch Z-late phase 1, P2: these two types were lifted out of the about-to-be-deleted
 * `ManageLabels` activity. The payload travels as an Intent extra between two screens, so what has
 * to survive the move is the JSON shape and the Json configuration — NOT the class's package, which
 * kotlinx never writes for a non-polymorphic type.
 */
class ManageLabelsContractTest {
    @Test fun modeStillHasItsFourValuesInOrder() {
        assertEquals(
            listOf("STUDYPAD", "WORKSPACE", "ASSIGN", "HIDELABELS"),
            Mode.entries.map { it.name },
        )
    }

    @Test fun aRoundTripPreservesTheMode() {
        val data = ManageLabelsData(mode = Mode.WORKSPACE)
        assertEquals(Mode.WORKSPACE, ManageLabelsData.fromJSON(data.toJSON()).mode)
    }

    @Test fun theModeIsEncodedByNameNotOrdinal() {
        // If this ever became an ordinal, reordering the enum would silently change every payload.
        assertTrue(ManageLabelsData(mode = Mode.ASSIGN).toJSON().contains("\"ASSIGN\""))
    }

    @Test fun anUnknownPropertyIsIgnoredOnDecode() {
        // This is the real point of the test: it pins WHICH Json instance the move uses.
        // CommonUtils.json sets ignoreUnknownKeys = true; the package-level `json` in the classic
        // ManageLabels.kt (deleted in slice S9) did not. Decoding must tolerate an extra property.
        val withExtra = """{"mode":"STUDYPAD","somethingNobodyKnows":42}"""
        assertEquals(Mode.STUDYPAD, ManageLabelsData.fromJSON(withExtra).mode)
    }

    @Test fun theModeDerivedFlagsAreUnchanged() {
        // assertTrue alone only pins the modes IN each set — pin the boundary with assertFalse
        // for a mode outside it too, so widening the set (e.g. showCheckboxes gaining WORKSPACE)
        // would also fail, not just narrowing it.
        assertTrue(ManageLabelsData(mode = Mode.HIDELABELS).showUnassigned)
        assertTrue(ManageLabelsData(mode = Mode.WORKSPACE).showUnassigned)
        assertFalse(ManageLabelsData(mode = Mode.ASSIGN).showUnassigned)

        assertTrue(ManageLabelsData(mode = Mode.ASSIGN).showCheckboxes)
        assertFalse(ManageLabelsData(mode = Mode.WORKSPACE).showCheckboxes)

        assertTrue(ManageLabelsData(mode = Mode.STUDYPAD).hideCategories)
        assertFalse(ManageLabelsData(mode = Mode.ASSIGN).hideCategories)
    }

    @Test fun contextSelectedItemsPicksTheBackingSetByMode() {
        // Two freshly-constructed instances are both empty sets either way, so equality can't
        // tell WORKSPACE's autoAssignLabels branch apart from the else -> selectedLabels branch.
        // Make the sets distinguishable and assert IDENTITY: this pins which field is returned,
        // not just that whatever is returned happens to compare equal.
        val workspaceData = ManageLabelsData(mode = Mode.WORKSPACE, autoAssignLabels = mutableSetOf(IdType()))
        assertSame(workspaceData.autoAssignLabels, workspaceData.contextSelectedItems)
        assertNotSame(workspaceData.selectedLabels, workspaceData.contextSelectedItems)

        val assignData = ManageLabelsData(mode = Mode.ASSIGN, selectedLabels = mutableSetOf(IdType()))
        assertSame(assignData.selectedLabels, assignData.contextSelectedItems)
        assertNotSame(assignData.autoAssignLabels, assignData.contextSelectedItems)
    }
}
