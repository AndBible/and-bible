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

package net.bible.android.control.page

import kotlinx.serialization.json.Json
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.sharedcore.bookmark.BookmarkDisplayStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The reader's Label payload. Its field names are mirrored by hand in
 * `bibleview-js/src/types/client-objects.ts` with nothing enforcing agreement, so the JSON key
 * names are asserted here rather than assumed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ClientBookmarkLabelTest {

    private fun label(style: BookmarkDisplayStyle, wholeVerse: BookmarkDisplayStyle?) =
        BookmarkEntities.Label(name = "Study", displayStyle = style, displayStyleWholeVerse = wholeVerse)

    @Test
    fun `an inherited whole-verse style is resolved before it reaches the reader`() {
        val client = ClientBookmarkLabel(label(BookmarkDisplayStyle.MARKER, null))
        assertEquals(BookmarkDisplayStyle.MARKER, client.style.displayStyle)
        assertEquals(BookmarkDisplayStyle.MARKER, client.style.displayStyleWholeVerse)
    }

    @Test
    fun `an explicit whole-verse style is passed through`() {
        val client = ClientBookmarkLabel(label(BookmarkDisplayStyle.HIGHLIGHT, BookmarkDisplayStyle.UNDERLINE))
        assertEquals(BookmarkDisplayStyle.HIGHLIGHT, client.style.displayStyle)
        assertEquals(BookmarkDisplayStyle.UNDERLINE, client.style.displayStyleWholeVerse)
    }

    @Test
    fun `the wire carries the two keys the TypeScript mirror expects, as enum names`() {
        val json = Json.encodeToString(
            ClientBookmarkLabel.serializer(),
            ClientBookmarkLabel(label(BookmarkDisplayStyle.HIDDEN, BookmarkDisplayStyle.MARKER)),
        )
        assertTrue(json, json.contains("\"displayStyle\":\"HIDDEN\""))
        assertTrue(json, json.contains("\"displayStyleWholeVerse\":\"MARKER\""))
    }
}
