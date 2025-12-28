/*
 * Copyright (c) 2022-2022 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.passage.NoSuchVerseException
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
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
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class BookmarkControlTest {
    private var testVerseCounter = 0
    private var currentTestVerse: String? = null
    private var testLabelCounter = 0
    private var currentTestLabel: String? = null
    private var bookmarkControl: BookmarkControl? = null

    @Before
    fun setUp() {
        val mockedWindowControl = Mockito.mock(WindowControl::class.java)
        bookmarkControl = BookmarkControl(mockedWindowControl, Mockito.mock(AndroidResourceProvider::class.java))
    }

    @After
    fun tearDown() {
        val bookmarks = bookmarkControl!!.allBibleBookmarks
        for (dto in bookmarks) {
            bookmarkControl!!.deleteBookmark(dto)
        }
        val labels = bookmarkControl!!.allLabels
        for (dto in labels) {
            bookmarkControl!!.deleteLabel(dto)
        }
        bookmarkControl = null
        resetDatabase()
    }

    @Test
    fun testAddBookmark() {
        try {
            val newDto = addTestVerse()
            Assert.assertEquals("New Bookmark key incorrect.  Test:" + currentTestVerse + " was:" + newDto!!.verseRange.name, newDto.verseRange.name, currentTestVerse)
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
            val bookmarks = bookmarkControl!!.allBibleBookmarks
            Assert.assertTrue(bookmarks.size == 3)
        } catch (e: Exception) {
            e.printStackTrace()
            Assert.fail("Exception:" + e.message)
        }
    }

    @Test
    fun testDeleteBookmark() {
        addTestVerse()
        var bookmarks = bookmarkControl!!.allBibleBookmarks
        val toDelete = bookmarks[0]
        bookmarkControl!!.deleteBookmark(toDelete)
        bookmarks = bookmarkControl!!.allBibleBookmarks
        for (bookmark in bookmarks) {
            Assert.assertFalse("delete failed", bookmark.id == toDelete.id)
        }
    }

    @Test
    fun testAddLabel() {
        try {
            val newDto = addTestLabel()
            Assert.assertEquals("New Label name incorrect.  Test:" + currentTestLabel + " was:" + newDto.name, newDto.name, currentTestLabel)
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
        bookmarkControl!!.setLabelsForBookmark(bookmark!!, labelList)
        val list1 = bookmarkControl!!.getBibleBookmarksWithLabel(label1)
        Assert.assertEquals(1, list1.size.toLong())
        Assert.assertEquals(bookmark, list1[0])
        val list2 = bookmarkControl!!.getBibleBookmarksWithLabel(label2)
        Assert.assertEquals(1, list2.size.toLong())
        Assert.assertEquals(bookmark, list2[0])

        // check 1 label is deleted if it is not linked
        val labelList2: MutableList<Label> = ArrayList()
        labelList2.add(label1)
        bookmarkControl!!.setLabelsForBookmark(bookmark, labelList2)
        val list3 = bookmarkControl!!.getBibleBookmarksWithLabel(label1)
        Assert.assertEquals(1, list3.size.toLong())
        val list4 = bookmarkControl!!.getBibleBookmarksWithLabel(label2)
        Assert.assertEquals(0, list4.size.toLong())
    }

    @Test
    fun testGetBookmarksWithLabel() {
        val bookmark = addTestVerse()
        val label1 = addTestLabel()
        val labelList: MutableList<Label> = ArrayList()
        labelList.add(label1)

        // add 2 labels and check they are saved
        bookmarkControl!!.setLabelsForBookmark(bookmark!!, labelList)
        val list1 = bookmarkControl!!.getBibleBookmarksWithLabel(label1)
        Assert.assertEquals(1, list1.size.toLong())
        Assert.assertEquals(bookmark, list1[0])
    }

    @Test
    fun testDeleteLabelsWithOrphanedBookmarks() {
        // Test that when deleting a StudyPad label, bookmarks that only have that label are NOT deleted by default
        // but can be deleted if explicitly requested
        
        // Create bookmarks and labels
        val bookmark1 = addTestVerse()
        val bookmark2 = addTestVerse()
        val bookmark3 = addTestVerse()
        
        val studyPadLabel = addTestLabel()  // This will be deleted
        val keepLabel = addTestLabel()      // This will be kept
        
        // bookmark1: only has studyPadLabel (would be orphaned, but should remain by default)
        bookmarkControl!!.setLabelsForBookmark(bookmark1!!, listOf(studyPadLabel))
        
        // bookmark2: has both studyPadLabel and keepLabel (should remain, and lose studyPadLabel)
        bookmarkControl!!.setLabelsForBookmark(bookmark2!!, listOf(studyPadLabel, keepLabel))
        
        // bookmark3: only has keepLabel (should remain unchanged)
        bookmarkControl!!.setLabelsForBookmark(bookmark3!!, listOf(keepLabel))
        
        // Verify initial state
        Assert.assertEquals(3, bookmarkControl!!.allBibleBookmarks.size)
        Assert.assertEquals(2, bookmarkControl!!.getBibleBookmarksWithLabel(studyPadLabel).size)
        Assert.assertEquals(2, bookmarkControl!!.getBibleBookmarksWithLabel(keepLabel).size)
        
        // Test 1: Delete labels without deleting orphaned bookmarks (default behavior)
        bookmarkControl!!.deleteLabels(listOf(studyPadLabel.id), deleteOrphanedBookmarks = false)
        
        // Verify results - all bookmarks should remain
        val remainingBookmarks = bookmarkControl!!.allBibleBookmarks
        Assert.assertEquals("Expected all 3 bookmarks to remain", 3, remainingBookmarks.size)
        
        // bookmark1 should remain but have no labels (orphaned)
        Assert.assertTrue("bookmark1 should remain", 
            remainingBookmarks.any { it.id == bookmark1.id })
        val bookmark1Labels = bookmarkControl!!.labelsForBookmark(
            remainingBookmarks.find { it.id == bookmark1.id }!!)
        Assert.assertEquals("bookmark1 should have no labels", 0, bookmark1Labels.size)
        
        // bookmark2 should remain and only have keepLabel
        Assert.assertTrue("bookmark2 should remain", 
            remainingBookmarks.any { it.id == bookmark2.id })
        val bookmark2Labels = bookmarkControl!!.labelsForBookmark(
            remainingBookmarks.find { it.id == bookmark2.id }!!)
        Assert.assertEquals("bookmark2 should have 1 label", 1, bookmark2Labels.size)
        Assert.assertEquals("bookmark2 should have keepLabel", keepLabel.id, bookmark2Labels[0].id)
        
        // bookmark3 should remain unchanged
        Assert.assertTrue("bookmark3 should remain", 
            remainingBookmarks.any { it.id == bookmark3.id })
        
        // Reset for second test - clean up database first
        val existingBookmarks = bookmarkControl!!.allBibleBookmarks
        for (bookmark in existingBookmarks) {
            bookmarkControl!!.deleteBookmark(bookmark)
        }
        val existingLabels = bookmarkControl!!.allLabels
        for (label in existingLabels) {
            bookmarkControl!!.deleteLabel(label)
        }
        setUp()
        
        // Test 2: Create the scenario again but with deleteOrphanedBookmarks = true
        val bookmark1b = addTestVerse()
        val bookmark2b = addTestVerse()
        val bookmark3b = addTestVerse()
        
        val studyPadLabelB = addTestLabel()
        val keepLabelB = addTestLabel()
        
        bookmarkControl!!.setLabelsForBookmark(bookmark1b!!, listOf(studyPadLabelB))
        bookmarkControl!!.setLabelsForBookmark(bookmark2b!!, listOf(studyPadLabelB, keepLabelB))
        bookmarkControl!!.setLabelsForBookmark(bookmark3b!!, listOf(keepLabelB))
        
        // Delete labels WITH deleting orphaned bookmarks
        bookmarkControl!!.deleteLabels(listOf(studyPadLabelB.id), deleteOrphanedBookmarks = true)
        
        val remainingBookmarks2 = bookmarkControl!!.allBibleBookmarks
        Assert.assertEquals("Expected 2 bookmarks to remain when deleting orphaned", 2, remainingBookmarks2.size)
        
        // bookmark1b should be deleted (it only had studyPadLabelB)
        Assert.assertFalse("bookmark1b should be deleted when deleteOrphanedBookmarks=true", 
            remainingBookmarks2.any { it.id == bookmark1b.id })
        
        // bookmark2b and bookmark3b should remain
        Assert.assertTrue("bookmark2b should remain", 
            remainingBookmarks2.any { it.id == bookmark2b.id })
        Assert.assertTrue("bookmark3b should remain", 
            remainingBookmarks2.any { it.id == bookmark3b.id })
    }

    @Test
    fun testVerseRange() {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 2), Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 5))
        val newBookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        val newDto = bookmarkControl!!.addOrUpdateBibleBookmark(newBookmark, null)
        Assert.assertThat(newDto.verseRange, IsEqual.equalTo(verseRange))
        Assert.assertThat(bookmarkControl!!.hasBookmarksForVerse(verseRange.start), IsEqual.equalTo(true))
    }

    @Test
    fun testIsBookmarkForAnyVerseRangeWithSameStart() {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10))
        val newBookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        bookmarkControl!!.addOrUpdateBibleBookmark(newBookmark, null)
        val startVerse = Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10)
        Assert.assertThat(bookmarkControl!!.hasBookmarksForVerse(startVerse), IsEqual.equalTo(true))

        // 1 has the same start as 10 but is not the same
        val verseWithSameStart = Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1)
        Assert.assertThat(bookmarkControl!!.hasBookmarksForVerse(verseWithSameStart), IsEqual.equalTo(false))
    }

    private fun addTestVerse(): BibleBookmarkWithNotes? {
        try {
            currentTestVerse = nextTestVerse
            return addBookmark(currentTestVerse)
        } catch (e: Exception) {
            Assert.fail("Error in verse:$currentTestVerse")
        }
        return null
    }

    @Throws(NoSuchVerseException::class)
    private fun addBookmark(verse: String?): BibleBookmarkWithNotes {
        val verseRange = VerseRangeFactory.fromString(KJV_VERSIFICATION, verse)
        val bookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        return bookmarkControl!!.addOrUpdateBibleBookmark(bookmark, null)
    }

    private fun addTestLabel(): Label {
        currentTestLabel = nextTestLabel
        val label = Label(new = true)
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
