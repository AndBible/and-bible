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

package net.bible.android.database.bookmarks

import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A newly created label inherits its whole-verse style. This is a KOTLIN default only: the column's
 * `@ColumnInfo(defaultValue = "1")` stays as it is on purpose, because changing it would alter the
 * table's identity hash and force a schema bump plus an update to iOS's byte-exact schema
 * transcription — and no runtime insert omits the column (Room names every column, and both
 * ExportStudyPads and cloud sync build their column lists from the live schema).
 *
 * Historical labels keep whatever is stored for them; nothing is migrated, so no existing bookmark
 * changes appearance.
 */
class LabelDefaultsTest {

    @Test
    fun `a new label inherits the whole-verse style`() {
        val label = BookmarkEntities.Label(name = "New")
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, label.displayStyle)
        assertNull(label.displayStyleWholeVerse)
    }

    @Test
    fun `inherit resolves to the selection style`() {
        val label = BookmarkEntities.Label(name = "New", displayStyle = BookmarkDisplayStyle.UNDERLINE)
        assertEquals(BookmarkDisplayStyle.UNDERLINE, label.effectiveWholeVerseStyle)
    }
}
