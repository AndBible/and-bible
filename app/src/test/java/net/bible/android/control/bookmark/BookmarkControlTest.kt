package net.bible.android.control.bookmark

import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities.Bookmark
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkStyle
import net.bible.service.format.usermarks.BookmarkFormatSupport
import net.bible.service.format.usermarks.MyNoteFormatSupport
import net.bible.service.sword.SwordContentFacade
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.hamcrest.MatcherAssert
import org.hamcrest.Matchers
import org.hamcrest.collection.IsIterableContainingInOrder
import org.hamcrest.core.IsEqual
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [28])
class BookmarkControlTest {
    private var testVerseCounter = 0
    private var currentTestVerse: String? = null
    private var testLabelCounter = 0
    private var currentTestLabel: String? = null
    private var bookmarkControl: BookmarkControl? = null
    private var bookmarkFormatSupport: BookmarkFormatSupport? = null

    @Before
    fun setUp() {
        bookmarkControl = BookmarkControl(SwordContentFacade(BookmarkFormatSupport(), MyNoteFormatSupport()), Mockito.mock(WindowControl::class.java), Mockito.mock(AndroidResourceProvider::class.java))
        bookmarkFormatSupport = BookmarkFormatSupport()
    }

    @After
    fun tearDown() {
        val bookmarks = bookmarkControl!!.allBookmarks
        for (dto in bookmarks) {
            bookmarkControl!!.deleteBookmark(dto, false)
        }
        val labels = bookmarkControl!!.allLabels
        for (dto in labels) {
            if(dto.id != null && dto.id!! > 0) {
                bookmarkControl!!.deleteLabel(dto)
            }
        }
        bookmarkControl = null
        resetDatabase()
    }

    @Test
    fun testAddBookmark() {
        try {
            val newDto = addTestVerse()
            Assert.assertEquals("New Bookmark key incorrect.  Test:" + currentTestVerse + " was:" + newDto!!.verseRange.name, newDto.verseRange.name, currentTestVerse)
            Assert.assertTrue("New Bookmark id incorrect", newDto.id!! > -1)
        } catch (e: Exception) {
            e.printStackTrace()
            Assert.fail("Exception:" + e.message)
        }
    }

    @Test
    fun testGetAllBookmarks() {
        try {
            addTestVerse()
            addTestVerse()
            addTestVerse()
            val bookmarks = bookmarkControl!!.allBookmarks
            Assert.assertTrue(bookmarks.size == 3)
        } catch (e: Exception) {
            e.printStackTrace()
            Assert.fail("Exception:" + e.message)
        }
    }

    @Test
    fun testDeleteBookmark() {
        addTestVerse()
        var bookmarks = bookmarkControl!!.allBookmarks
        val toDelete = bookmarks[0]
        bookmarkControl!!.deleteBookmark(toDelete, false)
        bookmarks = bookmarkControl!!.allBookmarks
        for (bookmark in bookmarks) {
            Assert.assertFalse("delete failed", bookmark.id == toDelete.id)
        }
    }

    @Test
    fun testAddLabel() {
        try {
            val newDto = addTestLabel()
            Assert.assertEquals("New Label name incorrect.  Test:" + currentTestLabel + " was:" + newDto.name, newDto.name, currentTestLabel)
            Assert.assertTrue("New Label id incorrect", newDto.id!! > -1)
        } catch (e: Exception) {
            e.printStackTrace()
            Assert.fail("Exception:" + e.message)
        }
    }

    @Test
    fun testSetBookmarkLabels() {
        val bookmark = addTestVerse()
        val label1 = addTestLabel()
        val label2 = addTestLabel()
        val labelList: MutableList<Label> = ArrayList()
        labelList.add(label1)
        labelList.add(label2)

        // add 2 labels and check they are saved
        bookmarkControl!!.setBookmarkLabels(bookmark!!, labelList)
        val list1 = bookmarkControl!!.getBookmarksWithLabel(label1)
        Assert.assertEquals(1, list1.size.toLong())
        Assert.assertEquals(bookmark, list1[0])
        val list2 = bookmarkControl!!.getBookmarksWithLabel(label2)
        Assert.assertEquals(1, list2.size.toLong())
        Assert.assertEquals(bookmark, list2[0])

        // check 1 label is deleted if it is not linked
        val labelList2: MutableList<Label> = ArrayList()
        labelList2.add(label1)
        bookmarkControl!!.setBookmarkLabels(bookmark, labelList2)
        val list3 = bookmarkControl!!.getBookmarksWithLabel(label1)
        Assert.assertEquals(1, list3.size.toLong())
        val list4 = bookmarkControl!!.getBookmarksWithLabel(label2)
        Assert.assertEquals(0, list4.size.toLong())
    }

    @Test
    fun testGetBookmarksWithLabel() {
        val bookmark = addTestVerse()
        val label1 = addTestLabel()
        val labelList: MutableList<Label> = ArrayList()
        labelList.add(label1)

        // add 2 labels and check they are saved
        bookmarkControl!!.setBookmarkLabels(bookmark!!, labelList)
        val list1 = bookmarkControl!!.getBookmarksWithLabel(label1)
        Assert.assertEquals(1, list1.size.toLong())
        Assert.assertEquals(bookmark, list1[0])
    }

    @Test
    fun testVerseRange() {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 2), Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 5))
        val newBookmark = Bookmark(verseRange)
        val newDto = bookmarkControl!!.addOrUpdateBookmark(newBookmark, false)
        Assert.assertThat(newDto.verseRange, IsEqual.equalTo(verseRange))
        Assert.assertThat(bookmarkControl!!.isBookmarkForKey(verseRange.start), IsEqual.equalTo(true))
    }

    @Test
    fun testIsBookmarkForAnyVerseRangeWithSameStart() {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10))
        val newBookmark = Bookmark(verseRange)
        bookmarkControl!!.addOrUpdateBookmark(newBookmark, false)
        val startVerse = Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10)
        Assert.assertThat(bookmarkControl!!.isBookmarkForKey(startVerse), IsEqual.equalTo(true))

        // 1 has the same start as 10 but is not the same
        val verseWithSameStart = Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1)
        Assert.assertThat(bookmarkControl!!.isBookmarkForKey(verseWithSameStart), IsEqual.equalTo(false))
    }

    @Test
    @Throws(Exception::class)
    fun testGetVersesWithBookmarksInPassage() {
        val passage = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1), Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10))

        // add bookmark in range
        val bookmark = addBookmark("ps.17.1-ps.17.2")
        var greenLabel = Label()
        greenLabel.name = "G"
        greenLabel.bookmarkStyle = BookmarkStyle.GREEN_HIGHLIGHT
        greenLabel = bookmarkControl!!.insertOrUpdateLabel(greenLabel)
        bookmarkControl!!.setBookmarkLabels(bookmark, listOf(greenLabel))
        addBookmark("ps.17.10")

        // add bookmark out of range
        addBookmark("ps.17.0")
        addBookmark("ps.17.11")

        // check only bookmark in range is returned
        val versesWithBookmarksInPassage = bookmarkFormatSupport!!.getVerseBookmarkStylesInPassage(passage)
        MatcherAssert.assertThat(versesWithBookmarksInPassage.size, IsEqual.equalTo(3))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[1], IsIterableContainingInOrder.contains(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2], IsIterableContainingInOrder.contains(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[10], IsIterableContainingInOrder.contains(BookmarkStyle.YELLOW_STAR)) // default bookmark style
    }

    @Test
    @Throws(Exception::class)
    fun testManyBookmarksInOneVerse() {
        val passage = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1), Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10))

        // add bookmark in range
        val bookmark = addBookmark("ps.17.1-ps.17.2")
        val bookmark2 = addBookmark("ps.17.2-ps.17.2")
        var greenLabel = Label()
        greenLabel.name = "G"
        greenLabel.bookmarkStyle = BookmarkStyle.GREEN_HIGHLIGHT
        greenLabel = bookmarkControl!!.insertOrUpdateLabel(greenLabel)
        var stargLabel = Label()
        stargLabel.name = "S"
        stargLabel.bookmarkStyle = BookmarkStyle.YELLOW_STAR
        stargLabel = bookmarkControl!!.insertOrUpdateLabel(stargLabel)
        bookmarkControl!!.setBookmarkLabels(bookmark, listOf(greenLabel))
        bookmarkControl!!.setBookmarkLabels(bookmark2, listOf(stargLabel))

        // check only bookmark in range is returned
        val versesWithBookmarksInPassage = bookmarkFormatSupport!!.getVerseBookmarkStylesInPassage(passage)
        MatcherAssert.assertThat(versesWithBookmarksInPassage.size, IsEqual.equalTo(2))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[1], Matchers.hasItem(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[1]!!.size, IsEqual.equalTo(1))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2], Matchers.hasItem(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2], Matchers.hasItem(BookmarkStyle.YELLOW_STAR))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2]!!.size, IsEqual.equalTo(2))
    }

    @Test
    @Throws(Exception::class)
    fun testManyBookmarksInOneVerse2() {
        val passage = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1), Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10))

        // add bookmark in range
        val bookmark = addBookmark("ps.17.2-ps.17.2")
        val bookmark2 = addBookmark("ps.17.1-ps.17.2")
        var label1 = Label()
        label1.name = "S"
        label1.bookmarkStyle = BookmarkStyle.YELLOW_STAR
        label1 = bookmarkControl!!.insertOrUpdateLabel(label1)
        var label2 = Label()
        label2.name = "G"
        label2.bookmarkStyle = BookmarkStyle.GREEN_HIGHLIGHT
        label2 = bookmarkControl!!.insertOrUpdateLabel(label2)
        bookmarkControl!!.setBookmarkLabels(bookmark, listOf(label1))
        bookmarkControl!!.setBookmarkLabels(bookmark2, listOf(label2))

        // check only bookmark in range is returned
        val versesWithBookmarksInPassage = bookmarkFormatSupport!!.getVerseBookmarkStylesInPassage(passage)
        MatcherAssert.assertThat(versesWithBookmarksInPassage.size, IsEqual.equalTo(2))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[1], Matchers.hasItem(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[1]!!.size, IsEqual.equalTo(1))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2], Matchers.hasItem(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2], Matchers.hasItem(BookmarkStyle.YELLOW_STAR))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2]!!.size, IsEqual.equalTo(2))
    }

    @Test
    @Throws(Exception::class)
    fun testManyBookmarksInOneVerse3() {
        val passage = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1), Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10))

        // add bookmark in range
        val bookmark = addBookmark("ps.17.2-ps.17.2")
        val bookmark2 = addBookmark("ps.17.1-ps.17.2")
        var label2 = Label()
        label2.name = "G"
        label2.bookmarkStyle = BookmarkStyle.GREEN_HIGHLIGHT
        label2 = bookmarkControl!!.insertOrUpdateLabel(label2)
        bookmarkControl!!.setBookmarkLabels(bookmark2, listOf(label2))

        // check only bookmark in range is returned
        val versesWithBookmarksInPassage = bookmarkFormatSupport!!.getVerseBookmarkStylesInPassage(passage)
        MatcherAssert.assertThat(versesWithBookmarksInPassage.size, IsEqual.equalTo(2))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[1], Matchers.hasItem(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[1]!!.size, IsEqual.equalTo(1))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2], Matchers.hasItem(BookmarkStyle.GREEN_HIGHLIGHT))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2], Matchers.hasItem(BookmarkStyle.YELLOW_STAR))
        MatcherAssert.assertThat(versesWithBookmarksInPassage[2]!!.size, IsEqual.equalTo(2))
    }

    private fun addTestVerse(): Bookmark? {
        try {
            currentTestVerse = nextTestVerse
            return addBookmark(currentTestVerse)
        } catch (e: Exception) {
            Assert.fail("Error in verse:$currentTestVerse")
        }
        return null
    }

    @Throws(NoSuchVerseException::class)
    private fun addBookmark(verse: String?): Bookmark {
        val verseRange = VerseRangeFactory.fromString(KJV_VERSIFICATION, verse)
        val bookmark = Bookmark(verseRange)
        return bookmarkControl!!.addOrUpdateBookmark(bookmark, false)
    }

    private fun addTestLabel(): Label {
        currentTestLabel = nextTestLabel
        val label = Label()
        label.name = currentTestLabel!!
        return bookmarkControl!!.insertOrUpdateLabel(label)
    }

    private val nextTestVerse: String
        private get() = TEST_VERSE_START + ++testVerseCounter

    private val nextTestLabel: String
        private get() = TEST_LABEL_START + ++testLabelCounter

    companion object {
        // keep changing the test verse
        private const val TEST_VERSE_START = "Psalms 119:"
        private val KJV_VERSIFICATION = Versifications.instance().getVersification("KJV")

        // keep changing the test label
        private const val TEST_LABEL_START = "Test label "
    }
}
