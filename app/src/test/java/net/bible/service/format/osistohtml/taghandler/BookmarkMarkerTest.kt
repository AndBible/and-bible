package net.bible.service.format.osistohtml.taghandler

import net.bible.android.control.bookmark.BookmarkStyle
import net.bible.service.format.osistohtml.OsisToHtmlParameters
import net.bible.service.format.osistohtml.osishandlers.OsisToHtmlSaxHandler.VerseInfo
import org.hamcrest.MatcherAssert
import org.hamcrest.collection.IsIterableContainingInOrder
import org.hamcrest.core.IsEqual
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * @author Martin Denham [mjdenham at gmail dot com]
 */
class BookmarkMarkerTest {
    private var osisToHtmlParameters: OsisToHtmlParameters? = null
    private var verseInfo: VerseInfo? = null
    private var bookmarkMarker: BookmarkMarker? = null
    var bookmarkStylesByBookmarkedVerse: MutableMap<Int, Set<BookmarkStyle>>? = null

    @Before
    fun setup() {
        osisToHtmlParameters = OsisToHtmlParameters()
        verseInfo = VerseInfo()
        osisToHtmlParameters!!.isShowBookmarks = true
        bookmarkStylesByBookmarkedVerse = HashMap()
        osisToHtmlParameters!!.bookmarkStylesByBookmarkedVerse = bookmarkStylesByBookmarkedVerse
        osisToHtmlParameters!!.setDefaultBookmarkStyle(BookmarkStyle.GREEN_HIGHLIGHT)
        bookmarkMarker = BookmarkMarker(osisToHtmlParameters, verseInfo!!)
    }

    @Test
    @Throws(Exception::class)
    fun testGetCustomBookmarkClass() {
        bookmarkStylesByBookmarkedVerse!![3] = setOf(BookmarkStyle.RED_HIGHLIGHT)
        verseInfo!!.currentVerseNo = 3
        val bookmarkClasses = bookmarkMarker!!.bookmarkClasses
        MatcherAssert.assertThat(bookmarkClasses, IsIterableContainingInOrder.contains("RED_HIGHLIGHT"))
    }

    @Test
    @Throws(Exception::class)
    fun testGetNoBookmarkClass() {
        bookmarkStylesByBookmarkedVerse!![3] = setOf(BookmarkStyle.RED_HIGHLIGHT)
        verseInfo!!.currentVerseNo = 4
        val bookmarkClasses = bookmarkMarker!!.bookmarkClasses
        MatcherAssert.assertThat(bookmarkClasses.size, IsEqual.equalTo(0))
    }
}
