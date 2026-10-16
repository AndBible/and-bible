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
package net.bible.android.view.activity.nav

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingMyDocumentExportsTest {
    @Test fun pending_targets_survive_a_saved_state_round_trip() {
        val before = PendingMyDocumentExports()
        before.documentIds = listOf("doc-a", "doc-b")
        before.pageIds = listOf("page-1"); before.documentsBatch = true
        val state = Bundle().also { before.saveTo(it) }

        // The recreated Activity gets a brand-new holder and restores it from the bundle.
        val after = PendingMyDocumentExports().also { it.restoreFrom(state) }
        assertEquals(listOf("doc-a", "doc-b"), after.takeDocumentIds())
        assertEquals(listOf("page-1"), after.takePageIds()); assertEquals(true, after.documentsBatch)
    }

    @Test fun take_clears_so_a_target_is_delivered_once() {
        val p = PendingMyDocumentExports()
        p.documentIds = listOf("x")
        assertEquals(listOf("x"), p.takeDocumentIds())
        assertEquals(emptyList<String>(), p.takeDocumentIds())
    }

    @Test fun restore_from_null_or_empty_state_is_empty() {
        val p = PendingMyDocumentExports().also { it.documentIds = listOf("stale") }
        p.restoreFrom(null)
        assertEquals(emptyList<String>(), p.documentIds)
        p.restoreFrom(Bundle())
        assertEquals(emptyList<String>(), p.pageIds)
    }
}
