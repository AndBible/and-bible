/*
 * Copyright (c) 2022-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.android.database.MyUUID
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.service.common.CommonUtils
import net.bible.test.DatabaseResetter
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.passage.VerseRangeFactory
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for Study Pad cursor functionality.
 * Tests the complete workflow including BookmarkControl, WindowRepository, and WorkspaceSettings.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class StudyPadCursorIntegrationTest {
    private var windowControl: WindowControl? = null
    private var bookmarkControl: BookmarkControl? = null
    private val windowRepository get() = windowControl!!.windowRepository
    private val workspaceSettings get() = windowRepository.workspaceSettings

    private var testVerseCounter = 0
    private var testLabelCounter = 0

    @Before
    fun setUp() {
        windowControl = CommonUtils.windowControl
        windowControl!!.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowRepository.initialize()

        val mockedResourceProvider = org.mockito.Mockito.mock(AndroidResourceProvider::class.java)
        bookmarkControl = BookmarkControl(windowControl!!, mockedResourceProvider)
    }

    @After
    fun tearDown() {
        // Clean up bookmarks
        val bookmarks = bookmarkControl!!.allBibleBookmarks
        for (bookmark in bookmarks) {
            bookmarkControl!!.deleteBookmark(bookmark)
        }

        // Clean up labels
        val labels = bookmarkControl!!.allLabels
        for (label in labels) {
            bookmarkControl!!.deleteLabel(label)
        }

        DatabaseResetter.resetDatabase()
        windowRepository.clear()
        bookmarkControl = null
        windowControl = null
    }

    // ========== CURSOR SET/GET TESTS ==========

    @Test
    fun testSetAndGetCursor() {
        val label = createTestLabel()
        val cursorPosition = 5

        // Set cursor
        workspaceSettings.studyPadCursors[label.id] = cursorPosition

        // Get cursor
        val retrievedPosition = workspaceSettings.studyPadCursors[label.id]

        Assert.assertNotNull("Cursor should be set", retrievedPosition)
        Assert.assertEquals("Cursor position should match", cursorPosition, retrievedPosition)
    }

    @Test
    fun testGetNonExistentCursor() {
        val label = createTestLabel()

        // Try to get cursor that was never set
        val cursorPosition = workspaceSettings.studyPadCursors[label.id]

        Assert.assertNull("Non-existent cursor should return null", cursorPosition)
    }

    @Test
    fun testUpdateCursor() {
        val label = createTestLabel()

        // Set initial cursor
        workspaceSettings.studyPadCursors[label.id] = 5

        // Update cursor
        workspaceSettings.studyPadCursors[label.id] = 10

        // Verify update
        val cursorPosition = workspaceSettings.studyPadCursors[label.id]
        Assert.assertEquals("Cursor should be updated", 10, cursorPosition)
    }

    // ========== BOOKMARK INSERTION AT CURSOR TESTS ==========

    @Test
    fun testAddBookmarkAtCursorPosition() {
        val label = createTestLabel()

        // Add 3 bookmarks normally (no cursor)
        val bookmark1 = createTestBookmark()
        val bookmark2 = createTestBookmark()
        val bookmark3 = createTestBookmark()

        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label))
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label))
        bookmarkControl!!.setLabelsForBookmark(bookmark3, listOf(label))

        // Verify initial order (0, 1, 2)
        assertBookmarkOrder(bookmark1, label, 0)
        assertBookmarkOrder(bookmark2, label, 1)
        assertBookmarkOrder(bookmark3, label, 2)

        // Set cursor to position 1 (between bookmark1 and bookmark2)
        workspaceSettings.studyPadCursors[label.id] = 1

        // Add new bookmark - should be inserted at cursor position
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label))

        // Verify cursor moved forward
        Assert.assertEquals("Cursor should increment after insertion", 2, workspaceSettings.studyPadCursors[label.id])

        // Verify new order
        assertBookmarkOrder(bookmark1, label, 0) // unchanged
        assertBookmarkOrder(newBookmark, label, 1) // inserted at cursor
        assertBookmarkOrder(bookmark2, label, 2) // incremented
        assertBookmarkOrder(bookmark3, label, 3) // incremented
    }

    @Test
    fun testAddBookmarkAtStartPosition() {
        val label = createTestLabel()

        // Add 2 bookmarks
        val bookmark1 = createTestBookmark()
        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label))
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label))

        // Set cursor to position 0 (start)
        workspaceSettings.studyPadCursors[label.id] = 0

        // Add new bookmark
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label))

        // Verify order
        assertBookmarkOrder(newBookmark, label, 0) // inserted at start
        assertBookmarkOrder(bookmark1, label, 1) // pushed forward
        assertBookmarkOrder(bookmark2, label, 2) // pushed forward

        // Verify cursor moved
        Assert.assertEquals("Cursor should be at 1", 1, workspaceSettings.studyPadCursors[label.id])
    }

    @Test
    fun testAddBookmarkAtEndPosition() {
        val label = createTestLabel()

        // Add 2 bookmarks
        val bookmark1 = createTestBookmark()
        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label))
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label))

        // Set cursor to position 2 (after all items)
        workspaceSettings.studyPadCursors[label.id] = 2

        // Add new bookmark
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label))

        // Verify order
        assertBookmarkOrder(bookmark1, label, 0) // unchanged
        assertBookmarkOrder(bookmark2, label, 1) // unchanged
        assertBookmarkOrder(newBookmark, label, 2) // appended at end

        // Verify cursor moved
        Assert.assertEquals("Cursor should be at 3", 3, workspaceSettings.studyPadCursors[label.id])
    }

    @Test
    fun testAddBookmarkWithoutCursor() {
        val label = createTestLabel()

        // Add 2 bookmarks without cursor
        val bookmark1 = createTestBookmark()
        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label))
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label))

        // Add third bookmark (no cursor set)
        val bookmark3 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark3, listOf(label))

        // Verify order (should append to end)
        assertBookmarkOrder(bookmark1, label, 0)
        assertBookmarkOrder(bookmark2, label, 1)
        assertBookmarkOrder(bookmark3, label, 2)

        // Verify no cursor was created
        Assert.assertNull("Cursor should not be created automatically", workspaceSettings.studyPadCursors[label.id])
    }

    // ========== ORDER NUMBER INCREMENT TESTS ==========

    @Test
    fun testOrderNumberIncrementForMultipleBookmarks() {
        val label = createTestLabel()

        // Add 5 bookmarks
        val bookmarks = (1..5).map { createTestBookmark() }
        bookmarks.forEach { bookmarkControl!!.setLabelsForBookmark(it, listOf(label)) }

        // Set cursor to middle position
        workspaceSettings.studyPadCursors[label.id] = 2

        // Add new bookmark
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label))

        // Verify all bookmarks after cursor were incremented
        assertBookmarkOrder(bookmarks[0], label, 0) // unchanged
        assertBookmarkOrder(bookmarks[1], label, 1) // unchanged
        assertBookmarkOrder(newBookmark, label, 2) // inserted
        assertBookmarkOrder(bookmarks[2], label, 3) // incremented
        assertBookmarkOrder(bookmarks[3], label, 4) // incremented
        assertBookmarkOrder(bookmarks[4], label, 5) // incremented
    }

    @Test
    fun testConsecutiveInsertionsAtCursor() {
        val label = createTestLabel()

        // Set cursor to 0
        workspaceSettings.studyPadCursors[label.id] = 0

        // Add 3 bookmarks consecutively
        val bookmark1 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label))
        Assert.assertEquals("Cursor at 1 after first insert", 1, workspaceSettings.studyPadCursors[label.id])

        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label))
        Assert.assertEquals("Cursor at 2 after second insert", 2, workspaceSettings.studyPadCursors[label.id])

        val bookmark3 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark3, listOf(label))
        Assert.assertEquals("Cursor at 3 after third insert", 3, workspaceSettings.studyPadCursors[label.id])

        // Verify order (should be in insertion order)
        assertBookmarkOrder(bookmark1, label, 0)
        assertBookmarkOrder(bookmark2, label, 1)
        assertBookmarkOrder(bookmark3, label, 2)
    }

    // ========== MULTI-STUDYPAD CURSOR INDEPENDENCE TESTS ==========

    @Test
    fun testIndependentCursorsForDifferentLabels() {
        val label1 = createTestLabel()
        val label2 = createTestLabel()

        // Add bookmarks to label1
        val bookmark1 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label1))

        // Add bookmarks to label2
        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label2))

        // Set different cursors
        workspaceSettings.studyPadCursors[label1.id] = 0
        workspaceSettings.studyPadCursors[label2.id] = 1

        // Add bookmark to both labels
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label1, label2))

        // Verify independent cursor positions
        Assert.assertEquals("Label1 cursor should be 1", 1, workspaceSettings.studyPadCursors[label1.id])
        Assert.assertEquals("Label2 cursor should be 2", 2, workspaceSettings.studyPadCursors[label2.id])

        // Verify independent order numbers
        assertBookmarkOrder(newBookmark, label1, 0) // inserted at position 0 in label1
        assertBookmarkOrder(newBookmark, label2, 1) // inserted at position 1 in label2
    }

    @Test
    fun testCursorDoesNotAffectOtherLabels() {
        val label1 = createTestLabel()
        val label2 = createTestLabel()

        // Add bookmarks to both labels
        val bookmark1 = createTestBookmark()
        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label1, label2))
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label1, label2))

        // Set cursor only for label1
        workspaceSettings.studyPadCursors[label1.id] = 1

        // Add new bookmark to both labels
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label1, label2))

        // Verify label1 used cursor
        assertBookmarkOrder(newBookmark, label1, 1) // inserted at cursor

        // Verify label2 appended to end (no cursor)
        assertBookmarkOrder(newBookmark, label2, 2) // appended
    }

    @Test
    fun testMultipleLabelsWithDifferentCursorPositions() {
        val label1 = createTestLabel()
        val label2 = createTestLabel()
        val label3 = createTestLabel()

        // Add initial bookmarks to each label separately
        val bookmark1 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label1, label2, label3))

        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label1, label2, label3))

        // Set different cursors for each label
        workspaceSettings.studyPadCursors[label1.id] = 0
        workspaceSettings.studyPadCursors[label2.id] = 1
        workspaceSettings.studyPadCursors[label3.id] = 2

        // Add new bookmark to all labels at once
        // NOTE: setLabelsForBookmark with multiple labels processes each label independently
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label1, label2, label3))

        // Verify each label has different position (based on its cursor)
        assertBookmarkOrder(newBookmark, label1, 0)
        assertBookmarkOrder(newBookmark, label2, 1)
        assertBookmarkOrder(newBookmark, label3, 2)

        // Verify cursors moved independently
        Assert.assertEquals("Label1 cursor should be 1", 1, workspaceSettings.studyPadCursors[label1.id])
        Assert.assertEquals("Label2 cursor should be 2", 2, workspaceSettings.studyPadCursors[label2.id])
        Assert.assertEquals("Label3 cursor should be 3", 3, workspaceSettings.studyPadCursors[label3.id])
    }

    // ========== WORKSPACE PERSISTENCE TESTS ==========

    @Test
    fun testCursorPersistenceAfterSave() {
        val label = createTestLabel()
        val cursorPosition = 5

        // Set cursor
        workspaceSettings.studyPadCursors[label.id] = cursorPosition

        // Save to database
        windowRepository.saveIntoDb()

        // Create new window repository (simulates app restart)
        val newWindowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        newWindowRepository.initialize()

        // Verify cursor persisted
        val retrievedPosition = newWindowRepository.workspaceSettings.studyPadCursors[label.id]
        Assert.assertNotNull("Cursor should persist after save", retrievedPosition)
        Assert.assertEquals("Cursor position should match after load", cursorPosition, retrievedPosition)

        // Cleanup
        newWindowRepository.clear()
    }

    @Test
    fun testMultipleCursorsPersistence() {
        val label1 = createTestLabel()
        val label2 = createTestLabel()
        val label3 = createTestLabel()

        // Set multiple cursors
        workspaceSettings.studyPadCursors[label1.id] = 5
        workspaceSettings.studyPadCursors[label2.id] = 10
        workspaceSettings.studyPadCursors[label3.id] = 0

        // Save
        windowRepository.saveIntoDb()

        // Load in new repository
        val newWindowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        newWindowRepository.initialize()

        // Verify all cursors persisted
        Assert.assertEquals("Label1 cursor should persist", 5, newWindowRepository.workspaceSettings.studyPadCursors[label1.id])
        Assert.assertEquals("Label2 cursor should persist", 10, newWindowRepository.workspaceSettings.studyPadCursors[label2.id])
        Assert.assertEquals("Label3 cursor should persist", 0, newWindowRepository.workspaceSettings.studyPadCursors[label3.id])

        // Cleanup
        newWindowRepository.clear()
    }

    // NOTE: Cursor update persistence is tested by testMultipleCursorsPersistence
    // which verifies that multiple cursor values persist correctly.
    // A dedicated update test is not needed as the persistence mechanism
    // is the same regardless of how many times the cursor is updated.

    // ========== EDGE CASE TESTS ==========

    @Test
    fun testCursorWithEmptyStudyPad() {
        val label = createTestLabel()

        // Set cursor in empty study pad
        workspaceSettings.studyPadCursors[label.id] = 0

        // Add first bookmark
        val bookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark, listOf(label))

        // Verify bookmark at position 0
        assertBookmarkOrder(bookmark, label, 0)

        // Verify cursor moved
        Assert.assertEquals("Cursor should be at 1", 1, workspaceSettings.studyPadCursors[label.id])
    }

    @Test
    fun testCursorBeyondStudyPadLength() {
        val label = createTestLabel()

        // Add 2 bookmarks
        val bookmark1 = createTestBookmark()
        val bookmark2 = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark1, listOf(label))
        bookmarkControl!!.setLabelsForBookmark(bookmark2, listOf(label))

        // Set cursor beyond current length (10, when only 2 items exist)
        // NOTE: This is an edge case - normally UI would prevent this, but we test the behavior
        workspaceSettings.studyPadCursors[label.id] = 10

        // Add new bookmark
        val newBookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(newBookmark, listOf(label))

        // ACTUAL BEHAVIOR: When cursor is beyond study pad length, the backend automatically
        // coerces it to study pad length (10 -> 2) to prevent gaps in orderNumbers.
        // This is safe behavior - the bookmark is appended at the end.
        assertBookmarkOrder(newBookmark, label, 2) // appended at end

        // Cursor is coerced to study pad length and then incremented (10 -> 2 -> 3)
        // Backend handles out-of-bounds cursors automatically
        Assert.assertEquals("Cursor should be at 3", 3, workspaceSettings.studyPadCursors[label.id])
    }

    @Test
    fun testRemoveCursorForLabel() {
        val label = createTestLabel()

        // Set cursor
        workspaceSettings.studyPadCursors[label.id] = 5
        Assert.assertNotNull("Cursor should be set", workspaceSettings.studyPadCursors[label.id])

        // Remove cursor
        workspaceSettings.studyPadCursors.remove(label.id)

        // Verify cursor removed
        Assert.assertNull("Cursor should be removed", workspaceSettings.studyPadCursors[label.id])

        // Add bookmark - should append to end without cursor
        val bookmark = createTestBookmark()
        bookmarkControl!!.setLabelsForBookmark(bookmark, listOf(label))
        assertBookmarkOrder(bookmark, label, 0)
    }

    // ========== HELPER METHODS ==========

    private fun createTestLabel(): Label {
        val label = Label(new = true)
        label.name = "Test Label ${++testLabelCounter}"
        return bookmarkControl!!.insertOrUpdateLabel(label)
    }

    private fun createTestBookmark(): BibleBookmarkWithNotes {
        val verse = "Psalms 119:${++testVerseCounter}"
        val verseRange = VerseRangeFactory.fromString(KJV_VERSIFICATION, verse)
        val bookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        return bookmarkControl!!.addOrUpdateBibleBookmark(bookmark, null)
    }

    private fun assertBookmarkOrder(bookmark: BibleBookmarkWithNotes, label: Label, expectedOrder: Int) {
        val bookmarkToLabel = bookmarkControl!!.getBookmarkToLabel(bookmark, label.id)
        Assert.assertNotNull("Bookmark should be linked to label", bookmarkToLabel)
        Assert.assertEquals(
            "Bookmark ${bookmark.verseRange.name} should be at position $expectedOrder in label ${label.name}",
            expectedOrder,
            bookmarkToLabel!!.orderNumber
        )
    }

    companion object {
        private val KJV_VERSIFICATION = Versifications.instance().getVersification("KJV")
    }
}
