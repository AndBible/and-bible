/*
 * Copyright (c) 2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
 *
 * This file is part of AndBible: Bible Study (http://github.com/AndBible/and-bible).
 *
 * AndBible is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndBible is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndBible. If not, see <http://www.gnu.org/licenses/>.
 */

package net.bible.service.cloudsync.documents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentBlockListTest {
    private class FakeStore : StringSetStore {
        var value: Set<String> = emptySet()
        override fun get() = value
        override fun set(value: Set<String>) { this.value = value }
    }

    @Test fun blocksAndUnblocks() {
        val store = FakeStore()
        val list = DocumentBlockList(store)
        assertFalse(list.isBlocked("KJV"))
        list.block("KJV")
        assertTrue(list.isBlocked("KJV"))
        assertEquals(setOf("KJV"), store.value)
        list.unblock("KJV")
        assertFalse(list.isBlocked("KJV"))
        assertEquals(emptySet<String>(), store.value)
    }

    @Test fun blockIsIdempotent() {
        val list = DocumentBlockList(FakeStore())
        list.block("KJV")
        list.block("KJV")
        assertEquals(setOf("KJV"), list.all())
    }
}
