/*
 * Copyright (c) 2020-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

package net.bible.android.view.activity.search

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test

class SearchHistoryStoreTest {

    @Test
    fun `updateHistory trims, deduplicates and prepends`() {
        val updated = SearchHistoryStore.updateHistory(
            existingHistory = listOf("john", "romans 8"),
            rawQuery = "  john  "
        )

        assertThat(updated, equalTo(listOf("john", "romans 8")))
    }

    @Test
    fun `updateHistory enforces max size`() {
        val existing = (1..20).map { "item$it" }
        val updated = SearchHistoryStore.updateHistory(
            existingHistory = existing,
            rawQuery = "new item"
        )

        assertThat(updated.size, equalTo(20))
        assertThat(updated.first(), equalTo("new item"))
        assertThat(updated.last(), equalTo("item19"))
    }

    @Test
    fun `deserialize drops empty values`() {
        val decoded = SearchHistoryStore.deserialize("first\u001F\u001F second \u001F")

        assertThat(decoded, equalTo(listOf("first", "second")))
    }
}
