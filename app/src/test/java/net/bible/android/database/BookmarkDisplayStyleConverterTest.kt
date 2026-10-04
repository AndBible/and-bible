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

package net.bible.android.database

import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The label's own display style and the workspace override that can replace it are the same four
 * values, and since round 9b they are also the same *encoding*: one INTEGER column each, 0..3.
 * These tests pin that contract — a reordering of [BookmarkDisplayStyle] would silently re-map
 * every stored row, and nothing else in the codebase would notice.
 */
class BookmarkDisplayStyleConverterTest {
    private val converters = Converters()

    @Test
    fun `ordinals equal the workspace override mode constants`() {
        assertEquals(WorkspaceEntities.WorkspaceLabelOverride.MODE_HIGHLIGHT, BookmarkDisplayStyle.HIGHLIGHT.ordinal)
        assertEquals(WorkspaceEntities.WorkspaceLabelOverride.MODE_UNDERLINE, BookmarkDisplayStyle.UNDERLINE.ordinal)
        assertEquals(WorkspaceEntities.WorkspaceLabelOverride.MODE_MARKER, BookmarkDisplayStyle.MARKER.ordinal)
        assertEquals(WorkspaceEntities.WorkspaceLabelOverride.MODE_HIDDEN, BookmarkDisplayStyle.HIDDEN.ordinal)
        assertEquals(4, BookmarkDisplayStyle.entries.size)
    }

    @Test
    fun `every value round-trips through the converter`() {
        for (style in BookmarkDisplayStyle.entries) {
            assertEquals(style, converters.toBookmarkDisplayStyle(converters.fromBookmarkDisplayStyle(style)))
        }
    }

    @Test
    fun `null round-trips as null, because null means inherit`() {
        assertNull(converters.fromBookmarkDisplayStyle(null))
        assertNull(converters.toBookmarkDisplayStyle(null))
    }

    @Test
    fun `an out-of-range value reads as HIGHLIGHT instead of crashing`() {
        // Corrupt or truncated data must not take the reader down; HIGHLIGHT is the style the
        // renderer's else-branch has always produced for "nothing set".
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, converters.toBookmarkDisplayStyle(7))
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, converters.toBookmarkDisplayStyle(-1))
    }
}
