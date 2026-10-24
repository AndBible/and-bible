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

package net.bible.android.control.bookmark

import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import org.junit.Assert.assertEquals
import org.junit.Test

/** BibleView's favourite-label cache (it feeds the synchronous set_config) follows label changes. */
class FavouriteIdsAfterTest {
    private val a = IdType()
    private val b = IdType()

    @Test fun `a label upserted as favourite joins once, and leaves when un-favourited`() {
        val joined = favouriteIdsAfter(listOf(a), BookmarkChange.LabelUpserted(Label(id = b, favourite = true)))
        assertEquals(listOf(a, b), joined)
        assertEquals(joined, favouriteIdsAfter(joined, BookmarkChange.LabelUpserted(Label(id = b, favourite = true))))
        assertEquals(listOf(a), favouriteIdsAfter(joined, BookmarkChange.LabelUpserted(Label(id = b, favourite = false))))
    }

    @Test fun `deleted labels leave and unrelated changes keep the list`() {
        assertEquals(listOf(b), favouriteIdsAfter(listOf(a, b), BookmarkChange.LabelsDeleted(listOf(a))))
        assertEquals(listOf(a, b), favouriteIdsAfter(listOf(a, b), BookmarkChange.BookmarksDeleted(listOf(a))))
    }
}
