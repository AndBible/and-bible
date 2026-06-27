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

package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlanBytesTest {
    private val sizes = mapOf("A" to 100L, "B" to 250L, "C" to 50L)

    @Test fun sumsMatchingInitials() {
        assertEquals(350L, sumPlanBytes(listOf("A", "B"), sizes))
    }

    @Test fun missingInitialsContributeZero() {
        assertEquals(100L, sumPlanBytes(listOf("A", "UNKNOWN"), sizes))
    }

    @Test fun emptyIsZero() {
        assertEquals(0L, sumPlanBytes(emptyList(), sizes))
    }
}
