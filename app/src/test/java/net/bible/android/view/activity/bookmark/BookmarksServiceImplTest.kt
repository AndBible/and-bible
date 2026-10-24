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

    /**
     * The list's reload is event-driven, and an event can arrive while the window repository is
     * reloading a workspace (`clear()` has run, no active window yet; re-entering the load is
     * guarded). Reading the active window's versification then threw NPE in the controller's
     * coroutine. Mid-load the rows fall back to each bookmark's own versification; the switch's
     * own notification reloads them with the real window afterwards.
     */
    @Test
    fun `loadRows during a workspace load uses the bookmark's own versification`() {
        val koin = org.koin.core.context.GlobalContext.get()
        val windowControl: net.bible.android.control.page.window.WindowControl = koin.get()
        val control: net.bible.android.control.bookmark.BookmarkControl = koin.get()
        val previous = windowControl.windowRepository
        val midLoad = net.bible.android.control.page.window.WindowRepository(
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main),
        )
        net.bible.android.control.page.window.WindowRepository::class.java.getDeclaredField("loadingFromDb")
            .apply { isAccessible = true }.setBoolean(midLoad, true)
        try {
            kotlinx.coroutines.runBlocking {
                control.addOrUpdateBibleBookmark(
                    net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes(
                        org.crosswire.jsword.passage.VerseRangeFactory.fromString(
                            org.crosswire.jsword.versification.system.Versifications.instance().getVersification("KJV"),
                            "Psalms 119:1",
                        ),
                        null, true, null,
                    ),
                    null,
                )
            }
            windowControl.windowRepository = midLoad
            val service: BookmarksServiceImpl = koin.get()
            val rows = kotlinx.coroutines.runBlocking {
                service.loadRows(filterIndex = 0, sort = BookmarkSortMode.BIBLE_ORDER, search = null, showNotes = false)
            }
            assertEquals(1, rows.size)
            assertTrue(rows.single().title, rows.single().title.contains("119:1"))
        } finally {
            windowControl.windowRepository = previous
            net.bible.test.DatabaseResetter.resetDatabase()
        }
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
