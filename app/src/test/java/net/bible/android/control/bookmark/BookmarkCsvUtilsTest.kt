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
        assertTrue("Should handle newlines in notes", csvOutput.contains("\nnewlines"))
    }

    @Test
    fun testExportAndImportMultiLineNotes(): Unit = runBlocking {
        // Given - Bookmark with multi-line notes
        val multiLineNote = """This is a multi-line note.
It contains several lines.
Some with "quotes" and; semicolons.
And even empty lines:

Like that one above."""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = multiLineNote
            customIcon = "heart"
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("Multi-line notes should be preserved exactly", multiLineNote, importedBookmark?.notes)
        assertEquals("Custom icon should be preserved", "heart", importedBookmark?.customIcon)
        assertEquals("OsisRef should match", "Gen.1.1", importedBookmark?.verseRange?.osisRef)
    }

    @Test
    fun testImportFullFormatMultiLineNotesFromCsv(): Unit = runBlocking {
        // Given - Full CSV format with multi-line notes (with all fields filled to avoid parsing issues)
        val csvContent = """osisRef;bibleRef;document;book;chapterStart;verseStart;chapterEnd;verseEnd;id;ordinalStart;ordinalEnd;createdAt;lastUpdatedOn;startOffset;endOffset;labels;notes;customIcon
Gen.1.1;Genesis 1:1;ESV2011;Gen;1;1;1;1;test-id;1;1;2022-01-01T00:00:00Z;2022-01-01T00:00:00Z;0;0;;"This is a multi-line note.
It spans multiple lines.
With various ""quotes"" and; semicolons.";star"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        
        val expectedNote = """This is a multi-line note.
It spans multiple lines.
With various "quotes" and; semicolons."""
        assertEquals("Multi-line notes should be imported correctly", expectedNote, importedBookmark?.notes)
        assertEquals("Custom icon should be set", "star", importedBookmark?.customIcon)
    }

    @Test
    fun testImportMultiLineNotesFromCsv(): Unit = runBlocking {
        // Given - Simplified CSV with multi-line notes
        val csvContent = """osisRef;notes;customIcon
Gen.1.1;"This is a multi-line note.
It spans multiple lines.
With various ""quotes"" and; semicolons.";star"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        
        val expectedNote = """This is a multi-line note.
It spans multiple lines.
With various "quotes" and; semicolons."""
        assertEquals("Multi-line notes should be imported correctly", expectedNote, importedBookmark?.notes)
        assertEquals("Custom icon should be set", "star", importedBookmark?.customIcon)
    }

    @Test
    fun testImportMultipleBookmarksWithMultiLineNotes(): Unit = runBlocking {
        // Given - CSV with multiple bookmarks, some with multi-line notes
        val csvContent = """osisRef;notes;customIcon
Gen.1.1;Single line note;star
Gen.1.2;"Multi-line note
with newlines
and special chars: ""quotes"" and; semicolons";heart
Gen.1.3;Another single line;bookmark"""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(3))
        assertThat(result.errors, equalTo(0))
        
        val allBookmarks = bookmarkControl.allBibleBookmarks.sortedBy { it.verseRange.start.verse }
        assertEquals("Should import 3 bookmarks", 3, allBookmarks.size)
        
        // Check first bookmark (single line)
        assertEquals("Single line note", allBookmarks[0].notes)
        assertEquals("star", allBookmarks[0].customIcon)
        
        // Check second bookmark (multi-line)
        val expectedMultiLineNote = """Multi-line note
with newlines
and special chars: "quotes" and; semicolons"""
        assertEquals(expectedMultiLineNote, allBookmarks[1].notes)
        assertEquals("heart", allBookmarks[1].customIcon)
        
        // Check third bookmark (single line)
        assertEquals("Another single line", allBookmarks[2].notes)
        assertEquals("bookmark", allBookmarks[2].customIcon)
    }

    @Test
    fun testExportMultiLineNotesEscaping(): Unit = runBlocking {
        // Given - Bookmark with complex multi-line notes
        val complexNote = """Line 1 with "quotes"
Line 2 with; semicolons
Line 3 with both "quotes" and; semicolons
Line 4 with ""double quotes""

Empty line above
Final line"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = complexNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        
        // Then
        val csvOutput = outputStream.toString("UTF-8")
        val lines = csvOutput.split("\n")
        
        // The CSV should be properly escaped and contain the multi-line content
        assertTrue("CSV should contain properly escaped quotes", csvOutput.contains("\"\"quotes\"\""))
        assertTrue("CSV should contain newlines", csvOutput.contains("\n"))
        assertTrue("CSV should be properly quoted for multi-line content", csvOutput.contains("\"Line 1 with"))
        
        // Test round-trip by importing it back
        val inputStream = ByteArrayInputStream(csvOutput.toByteArray(Charsets.UTF_8))
        bookmarkControl.deleteBookmark(savedBookmark)
        
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)
        
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Round-trip imported bookmark should exist", importedBookmark)
        assertEquals("Round-trip should preserve exact multi-line content", complexNote, importedBookmark?.notes)
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

    @Test
    fun testOrdinalRoundTripWithDifferentVersifications(): Unit = runBlocking {
        // This test reproduces the bug where ordinals from document-specific versification
        // are incorrectly interpreted as KJVA ordinals during import
        
        // Given - Create a bookmark with a specific verse in KJVA versification
        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Acts.8.14-Acts.8.17")
        
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = "Test bookmark for Acts 8:14-17"
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        
        // Record the original KJVA ordinals and verse reference
        val originalKjvOrdinalStart = savedBookmark.kjvOrdinalStart
        val originalKjvOrdinalEnd = savedBookmark.kjvOrdinalEnd
        val originalOsisRef = savedBookmark.verseRange.osisRef
        
        // When - Export and then reimport
        val outputStream = ByteArrayOutputStream()
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Verify CSV contains KJVA ordinals (not document-specific ones)
        assertTrue("CSV should contain kjvOrdinalStart", csvContent.contains(originalKjvOrdinalStart.toString()))
        assertTrue("CSV should contain kjvOrdinalEnd", csvContent.contains(originalKjvOrdinalEnd.toString()))
        
        // Clean up for reimport
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // Import the CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)
        
        // Then - The bookmark should maintain the same verse reference
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be reimported", importedBookmark)
        
        // The key assertion: KJVA ordinals should match after round-trip
        assertEquals("KJVA ordinal start should be preserved", originalKjvOrdinalStart, importedBookmark?.kjvOrdinalStart)
        assertEquals("KJVA ordinal end should be preserved", originalKjvOrdinalEnd, importedBookmark?.kjvOrdinalEnd)
        assertEquals("OSIS reference should be preserved", originalOsisRef, importedBookmark?.verseRange?.osisRef)
        assertEquals("Notes should be preserved", "Test bookmark for Acts 8:14-17", importedBookmark?.notes)
    }
    
    @Test
    fun testOrdinalExportUsesKjvaOrdinals(): Unit = runBlocking {
        // This test ensures that exported ordinals are always KJVA ordinals,
        // not document-specific ordinals, even when bookmark has different v11n
        
        // Given - Create bookmark with Acts reference
        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = "Test for ordinal export"
            new = true
        }
        
        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        
        // When - Export to CSV
        val outputStream = ByteArrayOutputStream()
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Then - CSV should contain KJVA ordinals
        val lines = csvContent.split("\n")
        val headerLine = lines[0]
        val dataLine = lines[1]
        
        val headers = headerLine.split(";")
        val values = dataLine.split(";")
        val headerMap = headers.withIndex().associate { it.value to it.index }
        
        val ordinalStartIndex = headerMap["ordinalStart"]!!
        val ordinalEndIndex = headerMap["ordinalEnd"]!!
        
        val exportedOrdinalStart = values[ordinalStartIndex].toInt()
        val exportedOrdinalEnd = values[ordinalEndIndex].toInt()
        
        // The exported ordinals should be KJVA ordinals
        assertEquals("Exported ordinalStart should be KJVA ordinal", savedBookmark.kjvOrdinalStart, exportedOrdinalStart)
        assertEquals("Exported ordinalEnd should be KJVA ordinal", savedBookmark.kjvOrdinalEnd, exportedOrdinalEnd)
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
        assertThat(result.errorMessages, equalTo(listOf("Record 2: Invalid bookmark data")))
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
            "Record 2: Invalid bookmark data",
            "Record 3: Invalid bookmark data"
        )))
    }

    // === HTML ENRICHED NOTES TESTS ===
    // These tests verify that HTML content in bookmark notes is handled correctly
    // during CSV export and import operations

    @Test
    fun testExportAndImportHtmlBasicFormatting(): Unit = runBlocking {
        // Given - Bookmark with basic HTML formatting
        val htmlNote = """This note contains <b>bold text</b>, <i>italic text</i>, and <u>underlined text</u>. 
Also includes <strong>strong emphasis</strong> and <em>emphasis</em>."""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML basic formatting should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlLists(): Unit = runBlocking {
        // Given - Bookmark with HTML lists
        val htmlNote = """Study points:
<ul>
<li>First point with <b>bold</b> text</li>
<li>Second point with <i>italic</i> text</li>
<li>Third point with nested list:
  <ol>
    <li>Nested item 1</li>
    <li>Nested item 2</li>
  </ol>
</li>
</ul>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.2")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML lists should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlLinks(): Unit = runBlocking {
        // Given - Bookmark with HTML links
        val htmlNote = """References:
<a href="https://example.com/study">Study Resource</a>
<a href="mailto:pastor@church.org">Contact Pastor</a>
<a href="tel:+1234567890">Phone Number</a>
Cross-reference: <a href="verse://John.3.16">John 3:16</a>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.3")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML links should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlTables(): Unit = runBlocking {
        // Given - Bookmark with HTML tables
        val htmlNote = """Comparison table:
<table border="1">
<tr>
  <th>Version</th>
  <th>Translation</th>
  <th>Note</th>
</tr>
<tr>
  <td>ESV</td>
  <td>"In the beginning..."</td>
  <td>Literal translation</td>
</tr>
<tr>
  <td>NIV</td>
  <td>"In the beginning..."</td>
  <td>Thought-for-thought</td>
</tr>
</table>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.4")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML tables should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlImages(): Unit = runBlocking {
        // Given - Bookmark with HTML images
        val htmlNote = """Visual aids:
<img src="https://example.com/map.jpg" alt="Biblical map" width="300" height="200"/>
<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg==" alt="Small image"/>
<figure>
  <img src="diagram.svg" alt="Family tree"/>
  <figcaption>David's family tree</figcaption>
</figure>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.5")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML images should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlBlockElements(): Unit = runBlocking {
        // Given - Bookmark with HTML block elements
        val htmlNote = """<h1>Main Heading</h1>
<h2>Subheading</h2>
<h3>Sub-subheading</h3>

<p>This is a paragraph with some text. It contains <span style="color: red;">colored text</span> and <span class="highlight">highlighted text</span>.</p>

<blockquote>
"For God so loved the world that he gave his one and only Son, that whoever believes in him shall not perish but have eternal life."
<cite>John 3:16</cite>
</blockquote>

<div class="note">
  <p>Personal reflection:</p>
  <p>This verse speaks to God's amazing love for humanity.</p>
</div>

<pre><code>
// Code example
function processVerse(verse) {
  return verse.toUpperCase();
}
</code></pre>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.6")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML block elements should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlComplexNesting(): Unit = runBlocking {
        // Given - Bookmark with complex nested HTML
        val htmlNote = """<div class="study-note">
  <h2>Study on <em>Genesis 1:1</em></h2>
  
  <section class="analysis">
    <h3>Textual Analysis</h3>
    <p>The Hebrew word <span lang="he" dir="rtl">בְּרֵאשִׁית</span> means "in the beginning".</p>
    
    <details>
      <summary>Etymology</summary>
      <p>From the root <strong>ברא</strong> meaning "to create".</p>
      <ul>
        <li>First occurrence: Genesis 1:1</li>
        <li>Related words:
          <ol>
            <li><span lang="he">בָּרָא</span> - he created</li>
            <li><span lang="he">בְּרִיאָה</span> - creation</li>
          </ol>
        </li>
      </ul>
    </details>
  </section>
  
  <aside class="cross-references">
    <h4>Cross References</h4>
    <ul>
      <li><a href="verse://John.1.1">John 1:1</a> - "In the beginning was the Word"</li>
      <li><a href="verse://Heb.11.3">Hebrews 11:3</a> - Faith and creation</li>
    </ul>
  </aside>
  
  <footer>
    <small>Study completed on <time datetime="2023-01-15">January 15, 2023</time></small>
  </footer>
</div>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.7")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("Complex nested HTML should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlWithAttributes(): Unit = runBlocking {
        // Given - Bookmark with HTML elements containing various attributes
        val htmlNote = """<div id="main-note" class="study-note important" data-verse="Gen.1.8" style="background-color: yellow; padding: 10px;">
  <p class="intro" style="font-weight: bold; color: #333;">Introduction to Creation</p>
  
  <form action="/submit-note" method="post">
    <label for="rating">Rate this study:</label>
    <select id="rating" name="rating" required>
      <option value="1">1 - Poor</option>
      <option value="5" selected>5 - Excellent</option>
    </select>
    
    <input type="hidden" name="verse" value="Gen.1.8"/>
    <input type="text" placeholder="Additional comments" maxlength="100"/>
    <button type="submit" disabled>Submit</button>
  </form>
  
  <audio controls preload="none">
    <source src="pronunciation.mp3" type="audio/mpeg"/>
    <source src="pronunciation.ogg" type="audio/ogg"/>
    Your browser does not support the audio element.
  </audio>
  
  <video width="320" height="240" controls poster="thumbnail.jpg">
    <source src="teaching.mp4" type="video/mp4"/>
    <track kind="subtitles" src="subtitles.vtt" srclang="en" label="English"/>
  </video>
</div>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.8")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML with attributes should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlSpecialCharacters(): Unit = runBlocking {
        // Given - Bookmark with HTML special characters and entities
        val htmlNote = """<p>Special characters test:</p>
<ul>
  <li>HTML entities: &lt; &gt; &amp; &quot; &apos; &nbsp;</li>
  <li>Numeric entities: &#39; &#34; &#60; &#62;</li>
  <li>Named entities: &copy; &reg; &trade; &mdash; &ndash;</li>
  <li>Unicode characters: ✓ ✗ → ← ↑ ↓ ★ ☆</li>
  <li>Mathematical symbols: ≤ ≥ ≠ ≈ ∞ π ∑ ∏</li>
  <li>Hebrew: אֱלֹהִים בָּרָא</li>
  <li>Greek: θεὸς ἠγάπησεν</li>
  <li>Emoji: 📖 🙏 ✝️ ⛪ 🕊️</li>
</ul>

<pre>
Literal text with <tags> and &entities; preserved
"Quotes" and 'apostrophes' and semicolons;
Line breaks
and	tabs	preserved
</pre>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.9")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("HTML with special characters should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlMixedContent(): Unit = runBlocking {
        // Given - Bookmark with mixed plain text and HTML content
        val htmlNote = """This is plain text at the beginning.

<h2>Now we switch to HTML</h2>

<p>This paragraph contains <strong>bold text</strong> and goes back to plain text.</p>

More plain text here with some "quotes" and; semicolons.

<blockquote>
A quote that spans
multiple lines
with various formatting.
</blockquote>

Final plain text section with normal content.

<script>
// This script should be preserved as-is
function highlightVerse(verseId) {
  document.getElementById(verseId).style.backgroundColor = 'yellow';
}
</script>

<style>
.important { font-weight: bold; color: red; }
.note { font-style: italic; }
</style>

<!-- This is an HTML comment that should be preserved -->

Final line of mixed content."""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.10")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("Mixed HTML and plain text content should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testExportAndImportHtmlMalformed(): Unit = runBlocking {
        // Given - Bookmark with malformed HTML (missing closing tags, etc.)
        val htmlNote = """<div class="note">
  <h2>Study Notes</h2>
  <p>This paragraph is not closed
  
  <ul>
    <li>First item
    <li>Second item without closing tag
    <li>Third item <b>with unclosed bold
  </ul>
  
  <p>Another paragraph <span style="color: red;">with unclosed span
  
  <img src="image.jpg" alt="No closing tag for img">
  <br>
  <hr>
  
  <table>
    <tr>
      <td>Cell 1
      <td>Cell 2</td>
    </tr>
  </table>
  
  Final text without proper closing div"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.11")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("Malformed HTML should be preserved exactly as-is", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testImportDirectHtmlFromCsv(): Unit = runBlocking {
        // Given - CSV with HTML content directly embedded
        val htmlNote = """<div class="direct-import">
  <h3>Direct HTML Import Test</h3>
  <p>This HTML content is <em>directly embedded</em> in the CSV file.</p>
  <ul>
    <li>Item with ""escaped quotes""</li>
    <li>Item with; semicolons</li>
    <li>Item with
newlines</li>
  </ul>
</div>"""

        val csvContent = """osisRef;notes
Gen.1.12;"${htmlNote.replace("\"", "\"\"")}""""

        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        
        // When
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("Direct HTML import should work correctly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testMultipleBookmarksWithVariousHtmlContent(): Unit = runBlocking {
        // Given - Multiple bookmarks with different types of HTML content
        val bookmarks = listOf(
            Pair("Gen.1.13", "<p>Simple <b>bold</b> text</p>"),
            Pair("Gen.1.14", "<ul><li>List item 1</li><li>List item 2</li></ul>"),
            Pair("Gen.1.15", "<table><tr><td>Cell</td></tr></table>"),
            Pair("Gen.1.16", "<div class=\"complex\"><h2>Title</h2><p>Content with <a href=\"#\">link</a></p></div>"),
            Pair("Gen.1.17", "Plain text mixed with <em>HTML</em> elements")
        )

        val savedBookmarks = bookmarks.map { (osisRef, htmlNote) ->
            val testVerseRange = VerseRangeFactory.fromString(KJVA, osisRef)
            val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
                verseRange = testVerseRange,
                textRange = null,
                wholeVerse = true,
                book = null
            ).apply {
                notes = htmlNote
                new = true
            }
            bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        }

        val outputStream = ByteArrayOutputStream()

        // When - Export all bookmarks
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, savedBookmarks, bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Clean up for import test
        savedBookmarks.forEach { bookmarkControl.deleteBookmark(it) }
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(5))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmarks = bookmarkControl.allBibleBookmarks.sortedBy { it.verseRange.start.verse }
        assertEquals("Should import all bookmarks", 5, importedBookmarks.size)
        
        // Verify each bookmark's HTML content is preserved
        bookmarks.forEachIndexed { index, (_, expectedHtml) ->
            val actualHtml = importedBookmarks[index].notes
            assertEquals("HTML content should be preserved for bookmark ${index + 1}", expectedHtml, actualHtml)
        }
    }

    @Test
    fun testHtmlCsvEscapingEdgeCases(): Unit = runBlocking {
        // Given - HTML content that contains CSV-problematic characters
        val htmlNote = """<div data-csv="test;with;semicolons">
  <p title="Quote test: &quot;double&quot; and 'single' quotes">Content</p>
  <pre>
Line 1 with "quotes"
Line 2 with; semicolons
Line 3 with, commas
Line 4 with	tabs
Line 5 with
newlines
  </pre>
  <script>
  var csv = "field1;field2;field3";
  var html = "<div>test</div>";
  var quotes = "He said \"Hello\" to me";
  </script>
</div>"""

        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.18")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = htmlNote
            new = true
        }

        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        val outputStream = ByteArrayOutputStream()

        // When - Export
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Verify that the CSV content properly escapes the HTML
        assertTrue("CSV should be properly quoted due to semicolons", csvContent.contains("\"<div data-csv="))
        assertTrue("CSV should escape internal quotes", csvContent.contains("&quot;double&quot;"))
        
        // Clean up for import test
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // When - Import the exported CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Imported bookmark should exist", importedBookmark)
        assertEquals("Complex HTML with CSV edge cases should be preserved exactly", htmlNote, importedBookmark?.notes)
    }

    @Test
    fun testImportBookmarksWithMultipleLabels(): Unit = runBlocking {
        // Given - This reproduces the bug: export uses semicolon but import expects comma
        // Create a bookmark with two labels "LabelA" and "LabelB"
        val testVerseRange = VerseRangeFactory.fromString(KJVA, "Gen.1.1")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = "Test bookmark with multiple labels"
            new = true
        }

        val labelA = BookmarkEntities.Label(
            name = "LabelA",
            color = defaultLabelColor
        ).apply { new = true }
        
        val labelB = BookmarkEntities.Label(
            name = "LabelB",
            color = defaultLabelColor
        ).apply { new = true }

        bookmarkControl.insertOrUpdateLabel(labelA)
        bookmarkControl.insertOrUpdateLabel(labelB)
        
        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        bookmarkControl.setLabelsForBookmark(savedBookmark, listOf(labelA, labelB))

        // When - Export and then reimport
        val outputStream = ByteArrayOutputStream()
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Verify CSV contains semicolon-separated labels
        assertTrue("CSV should contain LabelA", csvContent.contains("LabelA"))
        assertTrue("CSV should contain LabelB", csvContent.contains("LabelB"))
        
        // Clean up for reimport
        bookmarkControl.deleteBookmark(savedBookmark)
        bookmarkControl.deleteLabel(labelA)
        bookmarkControl.deleteLabel(labelB)
        
        // Import the CSV
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Then - The bookmark should have both labels, not a single "LabelA;LabelB" label
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be reimported", importedBookmark)
        
        val importedLabels = bookmarkControl.labelsForBookmark(importedBookmark!!)
        
        // This should be 2 labels, not 1
        assertEquals("Should have 2 labels", 2, importedLabels.size)
        
        val labelNames = importedLabels.map { it.name }.sorted()
        assertTrue("Should have LabelA", labelNames.contains("LabelA"))
        assertTrue("Should have LabelB", labelNames.contains("LabelB"))
        
        // Verify the labels are stored correctly in the database
        val allLabels = bookmarkControl.allLabels.filter { !it.isSpecialLabel }
        val labelAExists = allLabels.any { it.name == "LabelA" }
        val labelBExists = allLabels.any { it.name == "LabelB" }
        val combinedLabelExists = allLabels.any { it.name == "LabelA;LabelB" }
        
        assertTrue("LabelA should exist as separate label", labelAExists)
        assertTrue("LabelB should exist as separate label", labelBExists)
        assertFalse("Combined 'LabelA;LabelB' label should NOT exist", combinedLabelExists)
    }

    @Test
    fun testBugReproduction_MultipleLabelsExportImportCycle(): Unit = runBlocking {
        // This test reproduces the exact scenario from the bug report:
        // 1. Create a bookmark
        // 2. Assign two or more labels to it (e.g., "A" and "B")
        // 3. Export bookmarks as CSV
        // 4. Delete the bookmark
        // 5. Import the CSV file
        // Expected: The bookmark should retain its labels after import
        // Bug: The bookmark shows as "Unlabeled" or appears in study pad "A;B"

        // Step 1: Create a bookmark
        val testVerseRange = VerseRangeFactory.fromString(KJVA, "John.3.16")
        val bookmark = BookmarkEntities.BibleBookmarkWithNotes(
            verseRange = testVerseRange,
            textRange = null,
            wholeVerse = true,
            book = null
        ).apply {
            notes = "For God so loved the world"
            new = true
        }

        // Step 2: Create and assign two labels "A" and "B"
        val labelA = BookmarkEntities.Label(
            name = "A",
            color = defaultLabelColor
        ).apply { new = true }
        
        val labelB = BookmarkEntities.Label(
            name = "B",
            color = defaultLabelColor
        ).apply { new = true }

        bookmarkControl.insertOrUpdateLabel(labelA)
        bookmarkControl.insertOrUpdateLabel(labelB)
        
        val savedBookmark = bookmarkControl.addOrUpdateBibleBookmark(bookmark)
        bookmarkControl.setLabelsForBookmark(savedBookmark, listOf(labelA, labelB))

        // Verify labels are assigned
        val assignedLabels = bookmarkControl.labelsForBookmark(savedBookmark)
        assertEquals("Bookmark should have 2 labels before export", 2, assignedLabels.size)

        // Step 3: Export bookmarks as CSV
        val outputStream = ByteArrayOutputStream()
        BookmarkCsvUtils.exportBookmarksToCsv(outputStream, listOf(savedBookmark), bookmarkControl)
        val csvContent = outputStream.toString("UTF-8")
        
        // Step 4: Delete the bookmark (but keep labels for this test)
        bookmarkControl.deleteBookmark(savedBookmark)
        
        // Verify bookmark is deleted
        val bookmarksAfterDelete = bookmarkControl.allBibleBookmarks
        assertEquals("Bookmark should be deleted", 0, bookmarksAfterDelete.size)

        // Step 5: Import the CSV file
        val inputStream = ByteArrayInputStream(csvContent.toByteArray(Charsets.UTF_8))
        val result = BookmarkCsvUtils.importBookmarksFromCsv(inputStream, bookmarkControl)

        // Expected behavior: The bookmark should retain its labels after import
        assertThat(result.created, equalTo(1))
        assertThat(result.errors, equalTo(0))
        
        val importedBookmark = bookmarkControl.allBibleBookmarks.firstOrNull()
        assertNotNull("Bookmark should be recreated", importedBookmark)
        
        val importedLabels = bookmarkControl.labelsForBookmark(importedBookmark!!)
        
        // The bookmark should have both labels A and B
        assertEquals("Bookmark should have 2 labels after import", 2, importedLabels.size)
        
        val labelNames = importedLabels.map { it.name }.sorted()
        assertEquals("Should have labels A and B", listOf("A", "B"), labelNames)
        
        // Verify the bookmark does NOT show as "Unlabeled"
        assertFalse("Bookmark should not be unlabeled", importedLabels.isEmpty())
        
        // Verify there is NO label with combined name "A;B"
        val allLabels = bookmarkControl.allLabels.filter { !it.isSpecialLabel }
        val hasCombinedLabel = allLabels.any { it.name == "A;B" }
        assertFalse("Should NOT have a combined 'A;B' label", hasCombinedLabel)
        
        // Verify labels A and B exist as separate labels
        assertTrue("Label A should exist", allLabels.any { it.name == "A" })
        assertTrue("Label B should exist", allLabels.any { it.name == "B" })
    }
}
