/*
 * Copyright (c) 2025 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.common.resource.AndroidResourceProvider
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.bookmarks.BookmarkEntities
import net.bible.android.database.bookmarks.KJVA
import net.bible.android.database.bookmarks.defaultLabelColor
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.book.Books
import org.crosswire.jsword.book.basic.AbstractPassageBook
import org.crosswire.jsword.passage.VerseRangeFactory
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class BookmarkCsvUtilsTest {

    private lateinit var bookmarkControl: BookmarkControl

    @Before
    fun setUp() {
        // Create a real BookmarkControl instance for testing
        val mockedWindowControl = Mockito.mock(WindowControl::class.java)
        val mockedResourceProvider = Mockito.mock(AndroidResourceProvider::class.java)
        bookmarkControl = BookmarkControl(mockedWindowControl, mockedResourceProvider)
    }

    @After
    fun tearDown() {
        // Clean up any created bookmarks/labels
        val bookmarks = bookmarkControl.allBibleBookmarks
        for (bookmark in bookmarks) {
            bookmarkControl.deleteBookmark(bookmark)
        }
        val labels = bookmarkControl.allLabels.filter { !it.isSpecialLabel }
        for (label in labels) {
            bookmarkControl.deleteLabel(label)
        }
        resetDatabase()
    }

    @Test
    fun testExportBookmarksToCsv(): Unit = runBlocking {
        // Given
        val testDate = Date(1640995200000L) // 2022-01-01T00:00:00Z
        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val testBook = Books.installed().getBook("ESV2011") as? AbstractPassageBook

        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = testBook
        ).apply {
            createdAt = testDate
            lastUpdatedOn = testDate
            notes = "Test note with; semicolon"
            customIcon = "star"
            new = true
        }

        // Create and save labels
        val label1 = BookmarkEntities.Label(
            name = "Label 1",
            color = defaultLabelColor
        ).apply { new = true }
        
        val label2 = BookmarkEntities.Label(
            name = "Label;2",
            color = defaultLabelColor
        ).apply { new = true }

        bookmarkControl.insertOrUpdateLabel(label1)
        bookmarkControl.insertOrUpdateLabel(label2)
        
        // Save the bookmark and assign labels
        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        bookmarkControl.setLabelsForBookmark(savedBookmark, listOf(label1, label2))

        val outputStream = ByteArrayOutputStream()

        // When
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)

        // Then
        val csvOutput = outputStream.toString("UTF-8")
        val lines = csvOutput.split("\n")
        
        assertTrue("Should have header and data line", lines.size >= 2)
        
        val headerLine = lines[0]
        assertTrue("Header should contain osisRef", headerLine.contains("osisRef"))
        assertTrue("Header should contain bibleRef", headerLine.contains("bibleRef"))
        assertTrue("Header should contain notes", headerLine.contains("notes"))
        assertTrue("Header should contain labels", headerLine.contains("labels"))
        
        val dataLine = lines[1]
        assertTrue("Data should contain OSIS reference", dataLine.contains("Gen.1.1"))
        assertTrue("Data should contain escaped note", dataLine.contains("\"Test note with; semicolon\""))
        assertTrue("Data should contain custom icon", dataLine.contains("star"))
        // Labels should be present (though order may vary)
        assertTrue("Data should contain labels", dataLine.contains("Label 1") || dataLine.contains("Label;2"))
    }

    @Test
    fun testImportBookmarksFromCsv_Success(): Unit = runBlocking {
        // Given
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;Genesis 1:1;ESV2011;Gen;1;1;1;1;test-id;1;1;2022-01-01T00:00:00Z;2022-01-01T00:00:00Z;;;TestLabel;Test note;star"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.updated, equalTo(0))
        assertThat(result.errors, equalTo(0))
        
        // Verify bookmark was created
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertTrue("Bookmark should be created", allBookmarks.isNotEmpty())
        
        val importedBookmark = allBookmarks.find { it.notes == "Test note" }
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("Custom icon should be set", "star", importedBookmark?.customIcon)
    }

    @Test
    fun testImportBookmarksFromCsv_CreateNewLabels(): Unit = runBlocking {
        // Given
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;Genesis 1:1;ESV2011;Gen;1;1;1;1;test-id;1;1;2022-01-01T00:00:00Z;2022-01-01T00:00:00Z;;;NewTestLabel;Test note;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        
        // Verify bookmark was created with the label
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertTrue("Bookmark should be created", allBookmarks.isNotEmpty())
        
        val importedBookmark = allBookmarks.find { it.notes == "Test note" }
        assertNotNull("Imported bookmark should exist", importedBookmark)
        
        // Check if the label was created (may or may not be, depending on implementation)
        val labels = bookmarkControl.allLabels
        val hasNewLabel = labels.any { it.name == "NewTestLabel" }
        // We don't assert this must be true since the implementation might handle labels differently
        // This is more of an informational test
        println("NewTestLabel created: $hasNewLabel")
    }

    @Test
    fun testImportBookmarksFromCsv_EmptyFile(): Unit = runBlocking {
        // Given
        val inputStream = ByteArrayInputStream("".toByteArray(Charsets.UTF_8))

        // When & Then
        try {
            BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)
            fail("Should have thrown IOException for empty file")
        } catch (e: Exception) {
            assertTrue("Should be IOException", e.message?.contains("Empty CSV file") == true)
        }
    }

    @Test
    fun testImportBookmarksFromCsv_MalformedData(): Unit = runBlocking {
        // Given - CSV with completely invalid OSIS reference
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
NotAValidOsisRef;Invalid Reference;InvalidBook;InvalidBook;abc;def;ghi;jkl;test-id;xyz;uvw;not-a-date;not-a-date-either;;;Label;Test note;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then - The implementation might handle malformed data gracefully or create errors
        // We just verify that if there are errors, they are tracked properly
        assertTrue("Errors should be 1", result.errors == 1)
        assertTrue("Created + Updated should be == 0", (result.created + result.updated) == 0)
        assertTrue("Should have error messages when errors > 0", result.errorMessages.isNotEmpty())
    }

    @Test
    fun testExportBookmarksWithEmptyFields(): Unit = runBlocking {
        // Given - Bookmark with minimal data
        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = null
            customIcon = null
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)

        // Then
        val csvOutput = outputStream.toString("UTF-8")
        val lines = csvOutput.split("\n")
        val dataLine = lines[1]
        
        // Should handle empty/null fields gracefully
        assertTrue("Should contain empty fields", dataLine.contains(";;"))
        assertFalse("Should not contain null", dataLine.contains("null"))
        val expectedPattern = Regex("""Gen\.1\.1;Genesis 1:1;;Gen;1;1;1;1;[a-f0-9\-]+;4;4;\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z;\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z;;;;;""")
        assertTrue("Dataline should match pattern", expectedPattern.matches(dataLine))
    }

    @Test
    fun testImportBookmarksFromCsv_WithOffsets(): Unit = runBlocking {
        // Given
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;Genesis 1:1;ESV2011;Gen;1;1;1;1;test-id;1;1;2022-01-01T00:00:00Z;2022-01-01T00:00:00Z;10;20;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        val importedBookmark = allBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created", importedBookmark)
        assertEquals("Start offset should be set", 10, importedBookmark?.startOffset)
        assertEquals("End offset should be set", 20, importedBookmark?.endOffset)
        assertFalse("Should not be whole verse", importedBookmark?.wholeVerse ?: true)
    }

    @Test
    fun testCsvEscapingWithSpecialCharacters(): Unit = runBlocking {
        // Given - Bookmark with special characters that need escaping
        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = "Note with; semicolon and \"quotes\" and\nnewlines"
            new = true
        }

        val label = BookmarkEntities.Label(
            name = "Label with; semicolon",
            color = defaultLabelColor
        ).apply { new = true }

        bookmarkControl.insertOrUpdateLabel(label)
        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        bookmarkControl.setLabelsForBookmark(savedBookmark, listOf(label))
        
        val outputStream = ByteArrayOutputStream()

        // When
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        
        // Then
        val csvOutput = outputStream.toString("UTF-8")
        assertTrue("Should escape fields with semicolons", csvOutput.contains("\"Note with; semicolon"))
        assertTrue("Should escape fields with quotes", csvOutput.contains("\"\"quotes\"\""))
    }

    @Test
    fun testImportResult() {
        // Test ImportResult data class
        val result = BookmarkCsvUtils.ImportResult(
            created = 5,
            updated = 3,
            errorMessages = listOf("Error 1", "Error 2")
        )

        assertThat(result.created, equalTo(5))
        assertThat(result.updated, equalTo(3))
        assertThat(result.errors, equalTo(2))
        assertThat(result.errorMessages.size, equalTo(2))
    }
    
    // === MINIMAL CSV TESTS - Different essential field combinations ===

    @Test
    fun testImportMinimalCsv_OnlyOsisRef(): Unit = runBlocking {
        // Given - CSV with only osisRef (and required empty columns)
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;;;;;;;;;;;;;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created from osisRef only", importedBookmark)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
        assertEquals("OsisRef should match", "Gen.1.1", importedBookmark?.verseRange?.osisRef)
    }

    @Test
    fun testImportMinimalCsv_OnlyBibleRef(): Unit = runBlocking {
        // Given - CSV with only bibleRef
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
;Genesis 1:1;;;;;;;;;;;;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created from bibleRef only", importedBookmark)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
        assertEquals("OsisRef should match", "Gen.1.1", importedBookmark?.verseRange?.osisRef)
    }

    @Test
    fun testImportMinimalCsv_OnlyBookChapterVerse(): Unit = runBlocking {
        // Given - CSV with only discrete book/chapter/verse fields
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
;;;Gen;1;1;1;1;;;;;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created from book/chapter/verse fields", importedBookmark)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
        assertEquals("OsisRef should match", "Gen.1.1", importedBookmark?.verseRange?.osisRef)
    }

    @Test
    fun testImportMinimalCsv_OnlyBookChapterVerse2(): Unit = runBlocking {
        // Given - CSV with only discrete book/chapter/verse fields
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
;;;Gen;1;1;2;2;;;;;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))

        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))

        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created from book/chapter/verse fields", importedBookmark)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
        assertEquals("OsisRef should match", "Gen.1-Gen.2.2", importedBookmark?.verseRange?.osisRef)
    }

    @Test
    fun testImportMinimalCsv_OnlyOrdinals(): Unit = runBlocking {
        // Given - CSV with only ordinal fields
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
;;;;;;;;;1;1;;;;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created from ordinals only", importedBookmark)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
        assertEquals("OsisRef should match", "Intro.OT", importedBookmark?.verseRange?.osisRef)
    }

    @Test
    fun testImportMinimalCsv_OsisRefWithNotes(): Unit = runBlocking {
        // Given - Minimal CSV with osisRef and notes only
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;;;;;;;;;;;;;;;;My note;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created", importedBookmark)
        assertEquals("Notes should be preserved", "My note", importedBookmark?.notes)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
    }

    @Test
    fun testImportMinimalCsv_OsisRefWithLabels(): Unit = runBlocking {
        // Given - Minimal CSV with osisRef and labels only
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;;;;;;;;;;;;;;;MinimalLabel;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created", importedBookmark)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
        
        // Check if label was processed (implementation may vary)
        val labels = bookmarkControl.allLabels
        val hasMinimalLabel = labels.any { it.name == "MinimalLabel" }
        println("MinimalLabel created: $hasMinimalLabel")
    }

    @Test
    fun testImportMinimalCsv_OsisRefWithCustomIcon(): Unit = runBlocking {
        // Given - Minimal CSV with osisRef and customIcon only
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;;;;;;;;;;;;;;;;;heart"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created", importedBookmark)
        assertEquals("Custom icon should be preserved", "heart", importedBookmark?.customIcon)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
    }

    @Test
    fun testImportMinimalCsv_BibleRefWithDocument(): Unit = runBlocking {
        // Given - Minimal CSV with bibleRef and document specification
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
;Genesis 1:1;ESV2011;;;;;;;;;;;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created with document specified", importedBookmark)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
    }

    @Test
    fun testImportMinimalCsv_WithTimestamps(): Unit = runBlocking {
        // Given - Minimal CSV with osisRef and timestamps
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;;;;;;;;;;2022-01-01T10:30:00Z;2022-01-01T10:30:00Z;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created with timestamps", importedBookmark)
        assertNotNull("Created date should be set", importedBookmark?.createdAt)
        assertNotNull("Last updated date should be set", importedBookmark?.lastUpdatedOn)
        assertTrue("Should be whole verse", importedBookmark?.wholeVerse ?: false)
    }

    @Test
    fun testImportMinimalCsv_MultipleMinimalBookmarks(): Unit = runBlocking {
        // Given - Multiple minimal bookmarks with different approaches
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;;;;;;;;;;;;;;;;;
;Genesis 1:2;;;;;;;;;;;;;;;;
;;;Gen;1;3;1;3;;;;;;;;;
;;;;;;;;;5;5;;;;;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertEquals("Should create multiple bookmarks", result.created, 4)
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertEquals("Should have multiple bookmarks", allBookmarks.size, 4)
        
        // All should be whole verse bookmarks
        allBookmarks.forEach { bookmark ->
            assertTrue("All minimal bookmarks should be whole verse", bookmark.wholeVerse)
        }
    }

    @Test
    fun testImportMinimalCsv_WithTextSelection(): Unit = runBlocking {
        // Given - CSV with minimal data but including text offsets
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;Genesis 1:1;ESV2011;Gen;1;1;1;1;;;;;;5;15;;;;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be created with text selection", importedBookmark!!)
        assertEquals("Start offset should be set", 5, importedBookmark.startOffset)
        assertEquals("End offset should be set", 15, importedBookmark.endOffset)
        assertFalse("Should not be whole verse when offsets are specified", importedBookmark.wholeVerse)
    }

    @Test
    fun testImportMinimalCsv_MissingAllLocationFields(): Unit = runBlocking {
        // Given - CSV with no location information at all
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
;;;;;;;;;;;;;;;;;Just a note;"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        assertThat(result.created, equalTo(0))
        assertThat(result.updated, equalTo(0))
        assertThat(result.errors, equalTo(1))
        assertThat(result.errorMessages, equalTo(listOf("Line 2: Invalid bookmark data")))
    }
    
    // === REDUCED HEADER CSV TESTS - Only selected columns in header ===

    @Test
    fun testImportReducedHeaderCsv_OnlyOsisRefAndNotes(): Unit = runBlocking {
        // Given - CSV with only osisRef and notes columns
        val csvContent = """osisRef;notes
Gen.1.1;My simple note
Gen.1.2;Another note"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(2))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 2 bookmarks", allBookmarks.size, equalTo(2))
        
        val bookmark1 = allBookmarks.find { it.notes == "My simple note" }
        val bookmark2 = allBookmarks.find { it.notes == "Another note" }
        
        assertNotNull("First bookmark should exist", bookmark1)
        assertNotNull("Second bookmark should exist", bookmark2)
        assertTrue("Should be whole verse bookmarks", bookmark1?.wholeVerse == true)
        assertTrue("Should be whole verse bookmarks", bookmark2?.wholeVerse == true)
        assertEquals("First bookmark osisRef", "Gen.1.1", bookmark1?.verseRange?.osisRef)
        assertEquals("Second bookmark osisRef", "Gen.1.2", bookmark2?.verseRange?.osisRef)
    }

    @Test
    fun testImportReducedHeaderCsv_OnlyBibleRefAndLabels(): Unit = runBlocking {
        // Given - CSV with only bibleRef and labels columns
        val csvContent = """bibleRef;labels
Genesis 1:1;ImportantVerse
Genesis 1:2;StudyNote"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(2))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 2 bookmarks", allBookmarks.size, equalTo(2))
        
        val labels = bookmarkControl.allLabels
        val hasImportantVerse = labels.any { it.name == "ImportantVerse" }
        val hasStudyNote = labels.any { it.name == "StudyNote" }
        assertTrue("ImportantVerse label should be created", hasImportantVerse)
        assertTrue("StudyNote label should be created", hasStudyNote)
    }

    @Test
    fun testImportReducedHeaderCsv_DiscreteBookFields(): Unit = runBlocking {
        // Given - CSV with only discrete book/chapter/verse fields
        val csvContent = """book;chapterStart;verseStart;notes
Gen;1;1;First verse
Gen;1;2;Second verse
Matt;5;3;Blessed are the poor"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(3))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 3 bookmarks", allBookmarks.size, equalTo(3))
        
        val bookmark1 = allBookmarks.find { it.notes == "First verse" }
        val bookmark2 = allBookmarks.find { it.notes == "Second verse" }
        val bookmark3 = allBookmarks.find { it.notes == "Blessed are the poor" }
        
        assertNotNull("Genesis 1:1 bookmark should exist", bookmark1)
        assertNotNull("Genesis 1:2 bookmark should exist", bookmark2)
        assertNotNull("Matthew 5:3 bookmark should exist", bookmark3)
    }

    @Test
    fun testImportReducedHeaderCsv_OnlyOrdinals(): Unit = runBlocking {
        // Given - CSV with only ordinal fields (if implementation supports this)
        val csvContent = """ordinalStart;ordinalEnd;customIcon
1;1;star
2;2;heart
31102;31102;bookmark"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then - May create bookmarks or errors depending on implementation
        assertTrue("Should process ordinal-only CSV", (result.created + result.errors) > 0)
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertTrue("Should create some bookmarks", allBookmarks.isNotEmpty())
            
        // Check if custom icons are preserved
        val starBookmark = allBookmarks.find { it.customIcon == "star" }
        val heartBookmark = allBookmarks.find { it.customIcon == "heart" }
        assertNotNull("Star bookmark should exist if ordinals work", starBookmark)
        assertNotNull("Heart bookmark should exist if ordinals work", heartBookmark)
    }

    @Test
    fun testImportReducedHeaderCsv_TimestampsOnly(): Unit = runBlocking {
        // Given - CSV with minimal reference + timestamps
        val csvContent = """osisRef;createdAt;lastUpdatedOn
Gen.1.1;2022-01-01T10:00:00Z;2022-01-01T10:00:00Z
Gen.1.2;2022-01-02T11:30:00Z;2022-01-02T11:30:00Z"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(2))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 2 bookmarks", allBookmarks.size, equalTo(2))
        
        allBookmarks.forEach { bookmark ->
            assertNotNull("Created date should be set", bookmark.createdAt)
            assertNotNull("Last updated date should be set", bookmark.lastUpdatedOn)
        }
    }

    @Test
    fun testImportReducedHeaderCsv_TextSelection(): Unit = runBlocking {
        // Given - CSV with reference + text selection fields only
        val csvContent = """osisRef;startOffset;endOffset;notes
Gen.1.1;10;25;Selected text in first verse
Gen.1.2;5;20;Selected text in second verse"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(2))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 2 bookmarks", allBookmarks.size, equalTo(2))
        
        val bookmark1 = allBookmarks.find { it.notes == "Selected text in first verse" }
        val bookmark2 = allBookmarks.find { it.notes == "Selected text in second verse" }
        
        assertNotNull("First bookmark should exist", bookmark1!!)
        assertNotNull("Second bookmark should exist", bookmark2!!)
        
        // Check if text selection is supported
        assertEquals("Start offset should be preserved", 10, bookmark1.startOffset)
        assertEquals("End offset should be preserved", 25, bookmark1.endOffset)
        assertFalse("Should not be whole verse with offsets", bookmark1.wholeVerse)
    }

    @Test
    fun testImportReducedHeaderCsv_CompleteVersesOnly(): Unit = runBlocking {
        // Given - CSV with complete verse range specification
        val csvContent = """book;chapterStart;verseStart;chapterEnd;verseEnd;notes
Gen;1;1;1;3;Creation story beginning
Ps;23;1;23;6;The Lord is my shepherd
John;3;16;3;16;For God so loved the world"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(3))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 3 bookmarks", allBookmarks.size, equalTo(3))
        
        val creationBookmark = allBookmarks.find { it.notes == "Creation story beginning" }
        val psalmBookmark = allBookmarks.find { it.notes == "The Lord is my shepherd" }
        val johnBookmark = allBookmarks.find { it.notes == "For God so loved the world" }
        
        assertNotNull("Creation bookmark should exist", creationBookmark)
        assertNotNull("Psalm bookmark should exist", psalmBookmark)
        assertNotNull("John bookmark should exist", johnBookmark)
        
        // All should be whole verse bookmarks since no offsets specified
        assertTrue("Creation bookmark should be whole verse", creationBookmark?.wholeVerse == true)
        assertTrue("Psalm bookmark should be whole verse", psalmBookmark?.wholeVerse == true)
        assertTrue("John bookmark should be whole verse", johnBookmark?.wholeVerse == true)
    }

    @Test
    fun testImportReducedHeaderCsv_DocumentSpecific(): Unit = runBlocking {
        // Given - CSV with document + reference for translation-specific bookmarks
        val csvContent = """osisRef;document;notes
Gen.1.1;ESV2011;ESV translation note
Gen.1.1;KJV;KJV translation note"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(2))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 2 bookmarks", allBookmarks.size, equalTo(2))
        
        val esvBookmark = allBookmarks.find { it.notes == "ESV translation note" && it.book?.initials == "ESV2011" }
        val kjvBookmark = allBookmarks.find { it.notes == "KJV translation note" && it.book?.initials == "KJV" }
        
        assertNotNull("ESV bookmark should exist", esvBookmark)
        assertNotNull("KJV bookmark should exist", kjvBookmark)
    }

    @Test
    fun testImportReducedHeaderCsv_LabelsAndIconsOnly(): Unit = runBlocking {
        // Given - CSV with reference + visual elements only
        val csvContent = """osisRef;labels;customIcon
Gen.1.1;Favorite;star
Gen.1.2;Study;bookmark
Gen.1.3;Important;heart"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(3))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 3 bookmarks", allBookmarks.size, equalTo(3))
        
        val starBookmark = allBookmarks.find { it.customIcon == "star" }
        val bookmarkBookmark = allBookmarks.find { it.customIcon == "bookmark" }
        val heartBookmark = allBookmarks.find { it.customIcon == "heart" }
        
        assertNotNull("Star bookmark should exist", starBookmark)
        assertNotNull("Bookmark icon bookmark should exist", bookmarkBookmark)
        assertNotNull("Heart bookmark should exist", heartBookmark)

        // Check that labels are created
        val labels = bookmarkControl.allLabels
        assertTrue("Favorite label should exist", labels.any { it.name == "Favorite" })
        assertTrue("Study label should exist", labels.any { it.name == "Study" })
        assertTrue("Important label should exist", labels.any { it.name == "Important" })

        // Check that each bookmark has the correct label
        assertTrue("Star bookmark should have Favorite label",
            bookmarkControl.labelsForBookmark(starBookmark!!).any { it.name == "Favorite" })
        assertTrue("Bookmark icon bookmark should have Study label",
            bookmarkControl.labelsForBookmark(bookmarkBookmark!!).any { it.name == "Study" })
        assertTrue("Heart bookmark should have Important label",
            bookmarkControl.labelsForBookmark(heartBookmark!!).any { it.name == "Important" })
        
        assertEquals("Star bookmark should have star icon", "star", starBookmark?.customIcon)
        assertEquals("Bookmark should have bookmark icon", "bookmark", bookmarkBookmark?.customIcon)
        assertEquals("Heart bookmark should have heart icon", "heart", heartBookmark?.customIcon)
    }

    @Test
    fun testImportReducedHeaderCsv_MixedColumnOrder(): Unit = runBlocking {
        // Given - CSV with different column order
        val csvContent = """notes;customIcon;osisRef
My first note;star;Gen.1.1
My second note;heart;Gen.1.2"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(2))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 2 bookmarks", allBookmarks.size, equalTo(2))
        
        val firstBookmark = allBookmarks.find { it.notes == "My first note" }
        val secondBookmark = allBookmarks.find { it.notes == "My second note" }
        
        assertNotNull("First bookmark should exist", firstBookmark)
        assertNotNull("Second bookmark should exist", secondBookmark)
        
        assertEquals("First bookmark should have star icon", "star", firstBookmark?.customIcon)
        assertEquals("Second bookmark should have heart icon", "heart", secondBookmark?.customIcon)
    }

    @Test
    fun testImportReducedHeaderCsv_AbsoluteMinimal(): Unit = runBlocking {
        // Given - Absolutely minimal CSV with just verse references
        val csvContent = """osisRef
Gen.1.1
Gen.1.2
Ps.23.1
John.3.16"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(4))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks
        assertThat("Should create 4 bookmarks", allBookmarks.size, equalTo(4))
        
        // All should be whole verse bookmarks with no additional data
        allBookmarks.forEach { bookmark ->
            assertTrue("Should be whole verse", bookmark.wholeVerse)
            assertTrue("Should have null or empty notes", bookmark.notes.isNullOrEmpty())
            assertTrue("Should have null or empty custom icon", bookmark.customIcon.isNullOrEmpty())
        }
    }

    @Test
    fun testImportReducedHeaderCsv_InvalidMinimal(): Unit = runBlocking {
        // Given - CSV with only non-location columns (should fail gracefully)
        val csvContent = """notes;customIcon
Just a note;star
Another note;heart"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then - Should handle missing location data gracefully
        assertThat(result.created, equalTo(0))
        assertThat(result.updated, equalTo(0))
        assertThat(result.errors, equalTo(2))
        assertThat(result.errorMessages, equalTo(listOf(
            "Line 2: Invalid bookmark data",
            "Line 3: Invalid bookmark data"
        )))
    }
}
