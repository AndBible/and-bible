/*
 * Copyright (c) 2026 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.page

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityResultKindTest {
    @Test fun everyKindRoundTripsThroughItsExtraValue() {
        for (kind in ActivityResultKind.entries) {
            assertEquals(kind, ActivityResultKind.fromExtra(kind.name))
        }
    }

    @Test fun anUnknownOrAbsentValueIsNull() {
        assertNull(ActivityResultKind.fromExtra(null))
        assertNull(ActivityResultKind.fromExtra(""))
        assertNull(ActivityResultKind.fromExtra("NotAKind"))
        // Producer and reader both use `name`, so a near-miss must not resolve.
        assertNull(ActivityResultKind.fromExtra("bookmarks"))
    }

    @Test fun theExtraKeyIsStable() {
        assertEquals("abResultKind", ActivityResultKind.EXTRA)
    }

    @Test fun theSevenKindsTheDispatchNeedsAllExist() {
        assertEquals(
            listOf(
                "ChooseDocument", "MyDocuments", "MyDocumentPages",
                "PassageGrid", "Bookmarks", "ReadingProgress", "GenBookKey",
            ),
            ActivityResultKind.entries.map { it.name },
        )
    }
}
