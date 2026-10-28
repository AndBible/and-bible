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

import net.bible.android.common.toV11n
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.TextRange
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class ShareableBookmarkTest {
    /** A KJV-versification Bible: no Apocrypha, and different ordinals from the bookmarks' KJVA. */
    private val kjvBible = SwordBook(
        SwordBookMetaData("""
            [ShareTestKJV]
            Description=Share test Bible
            ModDrv=RawText
            Versification=KJV
            Lang=en
        """.trimIndent().encodeToByteArray(), "ShareTestKJV"),
        NullBackend(),
    )

    private fun bookless(start: Verse, end: Verse = start, textRange: TextRange? = null) =
        BibleBookmarkWithNotes(VerseRange(KJVA, start, end), textRange, textRange == null, null)

    @Test
    fun bookmarkWithItsOwnBibleIsSharedAsItIs() {
        val bookmark = BibleBookmarkWithNotes(VerseRange(KJVA, Verse(KJVA, BibleBook.JOHN, 3, 16)), null, true, kjvBible)
        assertSame(bookmark, shareableBookmark(bookmark) { error("default Bible must not be resolved") })
    }

    @Test
    fun booklessBookmarkIsMovedToTheDefaultBibleWithoutOffsets() {
        val bookmark = bookless(
            Verse(KJVA, BibleBook.JOHN, 3, 16), Verse(KJVA, BibleBook.JOHN, 3, 17), textRange = TextRange(4, 12),
        )

        val shared = shareableBookmark(bookmark) { kjvBible }

        val expected = bookmark.verseRange.toV11n(kjvBible.versification)
        assertSame(kjvBible, shared.book)
        assertEquals(kjvBible.versification, shared.v11n)
        assertEquals(expected.start.ordinal, shared.ordinalStart)
        assertEquals(expected.end.ordinal, shared.ordinalEnd)
        assertNull(shared.startOffset)
        assertNull(shared.endOffset)
        // The source bookmark is not modified.
        assertNull(bookmark.book)
        assertEquals(4, bookmark.startOffset)
    }

    @Test
    fun noBibleInstalledSharesTheBookmarkItself() {
        val bookmark = bookless(Verse(KJVA, BibleBook.JOHN, 3, 16))
        // As WindowControl.defaultBibleDoc does when no Bible is installed.
        assertSame(bookmark, shareableBookmark(bookmark) { throw NoSuchElementException("no Bible") })
    }

    @Test
    fun rangeTheDefaultBibleCannotHoldSharesTheBookmarkItself() {
        // KJV has no Apocrypha.
        val bookmark = bookless(Verse(KJVA, BibleBook.TOB, 1, 1))
        assertSame(bookmark, shareableBookmark(bookmark) { kjvBible })
    }
}
