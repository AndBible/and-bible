/*
 * Copyright (c) 2022-2026 Martin Denham, Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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

import net.bible.test.testAppSettings
import net.bible.test.testCoreStrings

import net.bible.test.testOrderedLauncher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmark
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmark
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkToLabel
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntry
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntryText
import net.bible.android.database.bookmarks.PARAGRAPH_BREAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_ID
import net.bible.android.database.bookmarks.SPEAK_LABEL_NAME
import net.bible.android.database.bookmarks.UNLABELED_LABEL_ID
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.room3.useWriterConnection
import net.bible.android.BibleApplication.Companion.application
import net.bible.service.db.exec
import net.bible.android.database.migrations.deduplicateSpecialLabels
import net.bible.service.db.DatabaseContainer
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
        bookmarkControl = BookmarkControl(mockedWindowControl, testAppSettings(), testCoreStrings(), testOrderedLauncher())
    }

    @After
    fun tearDown() {
        val bookmarks = runBlocking { bookmarkControl!!.allBibleBookmarks() }
        for (dto in bookmarks) {
            runBlocking { bookmarkControl!!.deleteBookmark(dto) }
        }
        val labels = runBlocking { bookmarkControl!!.allLabels() }
        for (dto in labels) {
            runBlocking { bookmarkControl!!.deleteLabel(dto) }
        }
        bookmarkControl = null
        resetDatabase()
    }

    @Test
    fun testAddBookmark() = runTest {
        try {
            val newDto = addTestVerse()
            Assert.assertEquals("New Bookmark key incorrect.  Test:" + currentTestVerse + " was:" + newDto!!.verseRange.getName(), newDto.verseRange.getName(), currentTestVerse)
        } catch (e: Exception) {
            e.printStackTrace()
            Assert.fail("Exception:" + e.message)
        }
    }

    @Test
    fun testGetAllBookmarks() = runTest {
        try {
            addTestVerse()
            addTestVerse()
            addTestVerse()
            val bookmarks = runBlocking { bookmarkControl!!.allBibleBookmarks() }
            Assert.assertTrue(bookmarks.size == 3)
        } catch (e: Exception) {
            e.printStackTrace()
            Assert.fail("Exception:" + e.message)
        }
    }

    @Test
    fun testDeleteBookmark() = runTest {
        addTestVerse()
        var bookmarks = runBlocking { bookmarkControl!!.allBibleBookmarks() }
        val toDelete = bookmarks[0]
        bookmarkControl!!.deleteBookmark(toDelete)
        bookmarks = runBlocking { bookmarkControl!!.allBibleBookmarks() }
        for (bookmark in bookmarks) {
            Assert.assertFalse("delete failed", bookmark.id == toDelete.id)
        }
    }

    @Test
    fun testAddLabel() = runTest {
        try {
            val newDto = addTestLabel()
            Assert.assertEquals("New Label name incorrect.  Test:" + currentTestLabel + " was:" + newDto.name, newDto.name, currentTestLabel)
        } catch (e: Exception) {
            e.printStackTrace()
            Assert.fail("Exception:" + e.message)
        }
    }

    @Test
    fun testSetBookmarkLabels() = runTest {
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
    fun testGetBookmarksWithLabel() = runTest {
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
    fun testDeleteLabelsWithOrphanedBookmarks() = runTest {
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
        Assert.assertEquals(3, runBlocking { bookmarkControl!!.allBibleBookmarks() }.size)
        Assert.assertEquals(2, bookmarkControl!!.getBibleBookmarksWithLabel(studyPadLabel).size)
        Assert.assertEquals(2, bookmarkControl!!.getBibleBookmarksWithLabel(keepLabel).size)
        
        // Test 1: Delete labels without deleting orphaned bookmarks (default behavior)
        bookmarkControl!!.deleteLabels(listOf(studyPadLabel.id), deleteOrphanedBookmarks = false)
        
        // Verify results - all bookmarks should remain
        val remainingBookmarks = runBlocking { bookmarkControl!!.allBibleBookmarks() }
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
        val existingBookmarks = runBlocking { bookmarkControl!!.allBibleBookmarks() }
        for (bookmark in existingBookmarks) {
            bookmarkControl!!.deleteBookmark(bookmark)
        }
        val existingLabels = runBlocking { bookmarkControl!!.allLabels() }
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
        
        val remainingBookmarks2 = runBlocking { bookmarkControl!!.allBibleBookmarks() }
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
    fun testVerseRange() = runTest {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 2), Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 5))
        val newBookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        val newDto = bookmarkControl!!.addOrUpdateBibleBookmark(newBookmark, null)
        Assert.assertThat(newDto.verseRange, IsEqual.equalTo(verseRange))
        Assert.assertThat(bookmarkControl!!.hasBookmarksForVerse(verseRange.start), IsEqual.equalTo(true))
    }

    @Test
    fun testIsBookmarkForAnyVerseRangeWithSameStart() = runTest {
        val verseRange = VerseRange(KJV_VERSIFICATION, Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10))
        val newBookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        bookmarkControl!!.addOrUpdateBibleBookmark(newBookmark, null)
        val startVerse = Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 10)
        Assert.assertThat(bookmarkControl!!.hasBookmarksForVerse(startVerse), IsEqual.equalTo(true))

        // 1 has the same start as 10 but is not the same
        val verseWithSameStart = Verse(KJV_VERSIFICATION, BibleBook.PS, 17, 1)
        Assert.assertThat(bookmarkControl!!.hasBookmarksForVerse(verseWithSameStart), IsEqual.equalTo(false))
    }

    @Test
    fun testSpecialLabelsCreatedWithCanonicalIds() = runTest {
        val speak = runBlocking { bookmarkControl!!.speakLabel() }
        Assert.assertEquals(SPEAK_LABEL_ID, speak.id)

        val unlabeled = runBlocking { bookmarkControl!!.labelUnlabelled() }
        Assert.assertEquals(UNLABELED_LABEL_ID, unlabeled.id)

        val paragraphBreak = runBlocking { bookmarkControl!!.paragraphBreakLabel() }
        Assert.assertEquals(PARAGRAPH_BREAK_LABEL_ID, paragraphBreak.id)
    }

    @Test
    fun testSpecialLabelIdHexRoundtrip() = runTest {
        // Migration uses IdType.toHex() for SQL X'...' literals.
        // Verify that toHex() roundtrips back to the same IdType.
        for (id in listOf(SPEAK_LABEL_ID, UNLABELED_LABEL_ID, PARAGRAPH_BREAK_LABEL_ID)) {
            val hex = id.toHex()
            Assert.assertEquals("Hex roundtrip failed for $id", 32, hex.length)
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            Assert.assertEquals("Roundtrip failed for $id", id, IdType.fromByteArray(bytes))
        }
    }

    /**
     * `deduplicateSpecialLabels` takes a driver `SQLiteConnection`, which Room 3 does not hand out
     * outside migrations (its connections are `PooledConnection`s). Run it on a second (bundled-driver)
     * connection to the container's bookmark database file, with foreign keys on like the Room connection
     * the old test used. Room's own connection has already committed everything (TRUNCATE journal), so the
     * two do not conflict.
     */
    private fun runDeduplicateSpecialLabels() {
        val path = application.getDatabasePath(net.bible.android.database.BookmarkDatabase.dbFileName).path
        BundledSQLiteDriver().open(path).use { connection ->
            // Room may still be reading this file (its invalidation tracker refreshes asynchronously after the
            // writer-connection ALTERs above); without a busy timeout a write here fails at once with
            // "database is locked" instead of waiting for that read to finish.
            connection.execSQL("PRAGMA busy_timeout=10000")
            connection.execSQL("PRAGMA foreign_keys=ON")
            deduplicateSpecialLabels(connection)
        }
    }

    /**
     * `deduplicateSpecialLabels` belongs to the 11->12 migration, so its INSERT names the six style
     * booleans that migration 12->13 replaced with `displayStyle`/`displayStyleWholeVerse`; in the
     * app it only ever sees a v11-shaped `Label`. These tests run it against the CURRENT (v13)
     * database, so put the legacy columns back first. What is under test is the reference remapping,
     * not the style columns the dedup copies along.
     */
    private fun addLegacyLabelStyleColumns(db: net.bible.android.database.BookmarkDatabase) = runBlocking {
        db.useWriterConnection { c ->
            for (column in listOf("markerStyle", "markerStyleWholeVerse", "underlineStyle",
                                  "underlineStyleWholeVerse", "hideStyle", "hideStyleWholeVerse")) {
                c.exec("ALTER TABLE Label ADD COLUMN $column INTEGER NOT NULL DEFAULT 0")
            }
        }
    }

    @Test
    fun testDeduplicateRemapsAllEntities() = runTest {
        val bookmarkDb = DatabaseContainer.instance.bookmarkDb
        val dao = bookmarkDb.bookmarkDao()

        // Insert an old-style speak label with a random ID
        val oldId = IdType()
        runBlocking { dao.insert(Label(id = oldId, name = SPEAK_LABEL_NAME, color = 0xFF0000)) }

        // 1. BibleBookmarkToLabel
        val bibleBookmark = addTestVerse()!!
        runBlocking { dao.insert(BibleBookmarkToLabel(bibleBookmark.id, oldId)) }

        // 2. BibleBookmark.primaryLabelId (set via raw SQL since there's no DAO method for this)
        val bibleBookmark2 = addTestVerse()!!
        runBlocking {
            bookmarkDb.useWriterConnection {
                it.exec("UPDATE BibleBookmark SET primaryLabelId = ? WHERE id = ?",
                    oldId.toByteArray(), bibleBookmark2.id.toByteArray())
            }
        }

        // 3. GenericBookmarkToLabel
        val genericBookmark = GenericBookmark(
            key = "test-key", bookInitials = "KJV",
            ordinalStart = null, ordinalEnd = null, startOffset = null, endOffset = null, customIcon = null
        )
        runBlocking { dao.insert(genericBookmark) }
        runBlocking { dao.insertGenericBookmarkToLabels(listOf(GenericBookmarkToLabel(genericBookmark.id, oldId))) }

        // 4. GenericBookmark.primaryLabelId
        val genericBookmark2 = GenericBookmark(
            key = "test-key-2", bookInitials = "KJV", primaryLabelId = oldId,
            ordinalStart = null, ordinalEnd = null, startOffset = null, endOffset = null, customIcon = null
        )
        runBlocking { dao.insert(genericBookmark2) }

        // 5. StudyPadTextEntry.labelId
        val studyPadEntry = StudyPadTextEntry(labelId = oldId, orderNumber = 0)
        runBlocking { dao.insert(studyPadEntry) }
        runBlocking { dao.insert(StudyPadTextEntryText(studyPadTextEntryId = studyPadEntry.id, text = "test")) }

        // Run migration dedup logic
        addLegacyLabelStyleColumns(bookmarkDb)
        runDeduplicateSpecialLabels()

        // Old label gone, canonical exists with inherited properties
        Assert.assertNull("Old label should be deleted", runBlocking { dao.labelById(oldId) })
        val canonicalLabel = runBlocking { dao.labelById(SPEAK_LABEL_ID) }!!
        Assert.assertEquals("Canonical label should inherit color", 0xFF0000, canonicalLabel.color)

        // 1. BibleBookmarkToLabel remapped
        val bibleLabels = bookmarkControl!!.labelsForBookmark(bibleBookmark)
        Assert.assertTrue("BibleBookmarkToLabel should reference canonical label",
            bibleLabels.any { it.id == SPEAK_LABEL_ID })

        // 2. BibleBookmark.primaryLabelId remapped
        val updatedBibleBookmark2 = runBlocking { dao.bibleBookmarkById(bibleBookmark2.id) }!!
        Assert.assertEquals("BibleBookmark.primaryLabelId should be remapped",
            SPEAK_LABEL_ID, updatedBibleBookmark2.primaryLabelId)

        // 3. GenericBookmarkToLabel remapped
        val genericLabels = bookmarkControl!!.labelsForBookmark(runBlocking { dao.genericBookmarkById(genericBookmark.id) }!!)
        Assert.assertTrue("GenericBookmarkToLabel should reference canonical label",
            genericLabels.any { it.id == SPEAK_LABEL_ID })

        // 4. GenericBookmark.primaryLabelId remapped
        val updatedGeneric2 = runBlocking { dao.genericBookmarkById(genericBookmark2.id) }!!
        Assert.assertEquals("GenericBookmark.primaryLabelId should be remapped",
            SPEAK_LABEL_ID, updatedGeneric2.primaryLabelId)

        // 5. StudyPadTextEntry.labelId remapped
        val updatedEntry = runBlocking { dao.studyPadTextEntryById(studyPadEntry.id) }!!
        Assert.assertEquals("StudyPadTextEntry.labelId should be remapped",
            SPEAK_LABEL_ID, updatedEntry.labelId)
    }

    @Test
    fun testDeduplicateMergesMultipleDuplicates() = runTest {
        val bookmarkDb = DatabaseContainer.instance.bookmarkDb
        val dao = bookmarkDb.bookmarkDao()

        // Insert multiple old-style speak labels with different random IDs
        val oldId1 = IdType()
        val oldId2 = IdType()
        runBlocking { dao.insert(Label(id = oldId1, name = SPEAK_LABEL_NAME, color = 0xFF0000)) }
        runBlocking { dao.insert(Label(id = oldId2, name = SPEAK_LABEL_NAME, color = 0x00FF00)) }

        // Create bookmarks associated with different old labels
        val bookmark1 = addTestVerse()!!
        val bookmark2 = addTestVerse()!!
        runBlocking { dao.insert(BibleBookmarkToLabel(bookmark1.id, oldId1)) }
        runBlocking { dao.insert(BibleBookmarkToLabel(bookmark2.id, oldId2)) }

        // Run migration dedup logic
        addLegacyLabelStyleColumns(bookmarkDb)
        runDeduplicateSpecialLabels()

        // Both old labels should be gone
        Assert.assertNull("Old label 1 should be deleted", runBlocking { dao.labelById(oldId1) })
        Assert.assertNull("Old label 2 should be deleted", runBlocking { dao.labelById(oldId2) })

        // Canonical label should exist with properties from one of the duplicates
        val canonicalLabel = runBlocking { dao.labelById(SPEAK_LABEL_ID) }!!
        Assert.assertTrue("Canonical label should inherit color from a duplicate",
            canonicalLabel.color == 0xFF0000 || canonicalLabel.color == 0x00FF00)

        // Both bookmarks should reference the canonical label
        val labels1 = bookmarkControl!!.labelsForBookmark(bookmark1)
        Assert.assertTrue("Bookmark1 should reference canonical label",
            labels1.any { it.id == SPEAK_LABEL_ID })
        val labels2 = bookmarkControl!!.labelsForBookmark(bookmark2)
        Assert.assertTrue("Bookmark2 should reference canonical label",
            labels2.any { it.id == SPEAK_LABEL_ID })
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
        return runBlocking { bookmarkControl!!.addOrUpdateBibleBookmark(bookmark, null) }
    }

    private fun addTestLabel(): Label {
        currentTestLabel = nextTestLabel
        val label = Label(new = true)
        label.name = currentTestLabel!!
        return runBlocking { bookmarkControl!!.insertOrUpdateLabel(label) }
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
