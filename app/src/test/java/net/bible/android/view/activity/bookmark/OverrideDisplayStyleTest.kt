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

import net.bible.android.database.IdType
import net.bible.android.database.WorkspaceEntities
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the list's override→style mapping against the renderer's own
 * [BookmarkEntities.Label.withStyleOverrides], which is what the reader actually obeys. Two
 * mappings of the same four ints in two files is exactly the kind of pair that drifts; this test is
 * the reason it cannot.
 */
class OverrideDisplayStyleTest {

    private fun styleAppliedByRenderer(mode: Int): BookmarkDisplayStyle {
        val override = WorkspaceEntities.WorkspaceLabelOverride(IdType(), IdType(), mode)
        // A label whose own style is deliberately NOT the override's, so the assertion cannot pass
        // by accident when the override is ignored.
        val label = BookmarkEntities.Label(name = "Study", displayStyle = BookmarkDisplayStyle.HIGHLIGHT)
        return label.withStyleOverrides(override).displayStyle
    }

    @Test
    fun every_override_mode_maps_to_the_style_the_renderer_applies() {
        listOf(
            WorkspaceEntities.WorkspaceLabelOverride.MODE_HIGHLIGHT,
            WorkspaceEntities.WorkspaceLabelOverride.MODE_UNDERLINE,
            WorkspaceEntities.WorkspaceLabelOverride.MODE_MARKER,
            WorkspaceEntities.WorkspaceLabelOverride.MODE_HIDDEN,
        ).forEach { mode ->
            assertEquals("mode $mode", styleAppliedByRenderer(mode), overrideDisplayStyle(mode))
        }
    }

    @Test
    fun no_override_and_an_unknown_mode_map_to_null() {
        assertNull(overrideDisplayStyle(null))
        assertNull(overrideDisplayStyle(99))
    }

    @Test
    fun an_override_also_takes_the_whole_verse_axis() {
        // Why the list draws the override tag with a FULL decoration rather than the half one the
        // selection axis gets: withStyleOverrides sets displayStyleWholeVerse = null (inherit), so
        // the override style is what BOTH axes draw in this workspace.
        val override = WorkspaceEntities.WorkspaceLabelOverride(
            IdType(), IdType(), WorkspaceEntities.WorkspaceLabelOverride.MODE_MARKER,
        )
        val label = BookmarkEntities.Label(
            name = "Study",
            displayStyle = BookmarkDisplayStyle.HIGHLIGHT,
            displayStyleWholeVerse = BookmarkDisplayStyle.UNDERLINE,
        )
        val overridden = label.withStyleOverrides(override)
        assertEquals(BookmarkDisplayStyle.MARKER, overridden.displayStyle)
        assertEquals(BookmarkDisplayStyle.MARKER, overridden.effectiveWholeVerseStyle)
    }
}
