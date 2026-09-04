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

import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.view.activity.bookmark.LabelEditContract.LabelData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelEditContractTest {
    private fun sample() = LabelData(
        isAssigning = false,
        label = BookmarkEntities.Label(name = "Test label"),
        isAutoAssign = false,
        isAutoAssignPrimary = false,
        isThisBookmarkSelected = true,
        isThisBookmarkPrimary = false,
    )

    @Test fun aRoundTripPreservesTheLabelNameAndFlags() {
        val decoded = LabelData.fromJSON(sample().toJSON())
        assertEquals("Test label", decoded.label.name)
        assertTrue(decoded.isThisBookmarkSelected)
        assertFalse(decoded.isAssigning)
        assertFalse(decoded.delete)
    }

    @Test fun anUnknownPropertyIsIgnoredOnDecode() {
        // Pins the Json instance, exactly as in ManageLabelsContractTest.
        val json = sample().toJSON().dropLast(1) + ""","somethingNobodyKnows":42}"""
        assertEquals("Test label", LabelData.fromJSON(json).label.name)
    }
}
