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
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `toLabelItem` is the ONLY place a Room label becomes a list item, so it is the only place the
 * list's style information can come from. These tests pin that both axes cross the seam, including
 * the null that means "inherit" — a mapper that defaulted it to the selection style would make the
 * list claim every label has an explicit whole-verse style.
 */
class LabelItemMappingTest {

    @Test
    fun `both style axes cross the seam`() {
        val item = BookmarkEntities.Label(
            name = "Study",
            displayStyle = BookmarkDisplayStyle.UNDERLINE,
            displayStyleWholeVerse = BookmarkDisplayStyle.MARKER,
        ).toLabelItem()

        assertEquals(BookmarkDisplayStyle.UNDERLINE, item.selectionStyle)
        assertEquals(BookmarkDisplayStyle.MARKER, item.wholeVerseStyle)
    }

    @Test
    fun `inherit stays null rather than being resolved`() {
        val item = BookmarkEntities.Label(
            name = "Study",
            displayStyle = BookmarkDisplayStyle.HIGHLIGHT,
            displayStyleWholeVerse = null,
        ).toLabelItem()

        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, item.selectionStyle)
        assertNull(item.wholeVerseStyle)
    }
}
