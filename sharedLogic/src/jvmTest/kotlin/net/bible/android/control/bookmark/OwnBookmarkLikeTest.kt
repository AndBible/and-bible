package net.bible.android.control.bookmark

import net.bible.android.database.DbDate
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.KJVA
import org.crosswire.jsword.book.sword.NullBackend
import org.crosswire.jsword.book.sword.SwordBook
import org.crosswire.jsword.book.sword.SwordBookMetaData
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/** "Bookmark this verse" on an add-on bookmark (#4166): which Bible the new own bookmark gets. */
class OwnBookmarkLikeTest {
    private fun bible(name: String) = SwordBook(
        SwordBookMetaData("[$name]\nDescription=t\nModDrv=RawText\nVersification=KJV\nLang=en".encodeToByteArray(), name),
        NullBackend(),
    )

    private val windowBible = bible("WinBible")
    private val rowBible = bible("RowBible")
    private val now = DbDate(1L)

    private fun addon(book: SwordBook?) =
        BibleBookmarkWithNotes(VerseRange(KJVA, Verse(KJVA, BibleBook.JOHN, 3, 16)), null, false, book).apply {
            startOffset = 3; endOffset = 9; notes = "n"; customIcon = "heart"
        }

    @Test
    fun windowBibleWinsOverTheRowsBook() {
        val a = addon(rowBible)
        val own = ownBibleBookmarkLike(a, windowBible, now)
        assertSame(windowBible, own.book)
        assertNotEquals(a.id, own.id)
        assertEquals(3, own.startOffset); assertEquals(9, own.endOffset)
        assertNull(own.notes); assertNull(own.customIcon)
    }

    @Test
    fun windowBibleIsUsedWhenTheRowHasNoBook() {
        assertSame(windowBible, ownBibleBookmarkLike(addon(null), windowBible, now).book)
    }

    @Test
    fun rowsBookIsUsedWhenTheWindowHasNoBible() {
        assertSame(rowBible, ownBibleBookmarkLike(addon(rowBible), null, now).book)
    }

    @Test
    fun noBookAtAllStaysEmpty() {
        assertNull(ownBibleBookmarkLike(addon(null), null, now).book)
    }
}
