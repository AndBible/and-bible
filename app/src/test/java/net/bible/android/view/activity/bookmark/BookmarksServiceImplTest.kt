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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.bookmarks.BookmarkSortOrder
import net.bible.sharedcore.bookmark.BookmarkSortMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class BookmarksServiceImplTest {

    @Test
    fun `htmlToStyledText bolds only the b span`() {
        val result = htmlToStyledText("a<b>b</b>c")
        assertEquals("abc", result.plainText())
        assertEquals(3, result.runs.size)
        assertEquals("a" to false, result.runs[0].text to result.runs[0].bold)
        assertEquals("b" to true, result.runs[1].text to result.runs[1].bold)
        assertEquals("c" to false, result.runs[2].text to result.runs[2].bold)
    }

    @Test
    fun `htmlToStyledText unescapes basic entities outside bold`() {
        val result = htmlToStyledText("Adam &amp; Eve say &quot;hi&quot;<b>fast</b>")
        assertEquals("Adam & Eve say \"hi\"fast", result.plainText())
        assertTrue(result.runs.last().bold)
    }

    @Test
    fun `htmlToStyledText strips tags other than b`() {
        val result = htmlToStyledText("line1<br>line2<i>note</i>")
        assertEquals("line1line2note", result.plainText())
        assertFalse(result.runs.any { it.bold })
    }

    @Test
    fun `htmlToStyledText on plain text with no tags yields one unbolded run`() {
        val result = htmlToStyledText("just plain text")
        assertEquals("just plain text", result.plainText())
        assertTrue(result.runs.none { it.bold })
    }

    @Test
    fun `sort mode round-trips through the classic BookmarkSortOrder pref`() {
        for (mode in BookmarkSortMode.entries) {
            val order = mode.toClassicSortOrder()
            assertEquals(mode, order.toSortMode())
        }
    }

    @Test
    fun `unreachable classic sort orders fall back to BIBLE_ORDER`() {
        assertEquals(BookmarkSortMode.BIBLE_ORDER, BookmarkSortOrder.LAST_UPDATED.toSortMode())
        assertEquals(BookmarkSortMode.BIBLE_ORDER, BookmarkSortOrder.ORDER_NUMBER.toSortMode())
    }
}
