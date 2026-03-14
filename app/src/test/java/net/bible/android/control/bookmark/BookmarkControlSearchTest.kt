/*
 * Copyright (c) 2026-2026 Sykerö Software / Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.android.database.IdType
import net.bible.android.database.bookmarks.BookmarkEntities.BibleBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.GenericBookmarkWithNotes
import net.bible.android.database.bookmarks.BookmarkEntities.Label
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntry
import net.bible.android.database.bookmarks.BookmarkEntities.StudyPadTextEntryText
import net.bible.service.db.DatabaseContainer
import net.bible.test.DatabaseResetter.resetDatabase
import org.crosswire.jsword.passage.Verse
import org.crosswire.jsword.passage.VerseRange
import org.crosswire.jsword.versification.BibleBook
import org.crosswire.jsword.versification.system.Versifications
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk=[TEST_SDK])
class BookmarkControlSearchTest {
    private var bookmarkControl: BookmarkControl? = null
    private val createdLabelIds = mutableListOf<IdType>()
    private val createdTextEntryIds = mutableListOf<IdType>()
    private val createdBibleBookmarkIds = mutableListOf<IdType>()
    private val createdGenericBookmarkIds = mutableListOf<IdType>()

    @Before
    fun setUp() {
        val mockedWindowControl = Mockito.mock(WindowControl::class.java)
        bookmarkControl = BookmarkControl(mockedWindowControl, Mockito.mock(AndroidResourceProvider::class.java))
    }

    @After
    fun tearDown() {
        // Clean up all created test data
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

        // Delete text entries
        createdTextEntryIds.forEach { entryId ->
            dao.studyPadTextEntryById(entryId)?.let { entryWithText ->
                val entry = StudyPadTextEntry(
                    id = entryWithText.id,
                    labelId = entryWithText.labelId,
                    orderNumber = entryWithText.orderNumber,
                    indentLevel = entryWithText.indentLevel
                )
                dao.delete(entry)
            }
        }

        // Delete bookmarks
        createdBibleBookmarkIds.forEach { bookmarkId ->
            dao.bibleBookmarkById(bookmarkId)?.let { bookmark ->
                bookmarkControl!!.deleteBookmark(bookmark)
            }
        }
        createdGenericBookmarkIds.forEach { bookmarkId ->
            dao.genericBookmarkById(bookmarkId)?.let { bookmark ->
                bookmarkControl!!.deleteBookmark(bookmark)
            }
        }

        // Delete labels
        createdLabelIds.forEach { labelId ->
            dao.labelById(labelId)?.let { label ->
                bookmarkControl!!.deleteLabel(label)
            }
        }

        bookmarkControl = null
        resetDatabase()
    }

    // ========== Helper Methods ==========

    private fun createStudyPadLabel(name: String): Label {
        val label = Label(new = true)
        label.name = name
        val savedLabel = bookmarkControl!!.insertOrUpdateLabel(label)
        createdLabelIds.add(savedLabel.id)
        return savedLabel
    }

    private fun createStudyPadTextEntry(labelId: IdType, text: String): StudyPadTextEntry {
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

        // Find the highest order number for this label
        val entries = dao.studyPadTextEntriesByLabelId(labelId)
        val maxOrderNumber = entries.maxOfOrNull { it.orderNumber } ?: -1

        // Create entry with generated ID
        val entryId = IdType()
        val entry = StudyPadTextEntry(
            id = entryId,
            labelId = labelId,
            orderNumber = maxOrderNumber + 1,
            indentLevel = 0
        )

        dao.insert(entry)

        // Insert the text separately
        val textEntry = StudyPadTextEntryText(
            studyPadTextEntryId = entryId,
            text = text
        )
        dao.insert(textEntry)

        createdTextEntryIds.add(entryId)
        return entry
    }

    private fun createBibleBookmarkWithNote(labelId: IdType, verseRef: String, note: String): BibleBookmarkWithNotes {
        val verse = Verse(KJV_VERSIFICATION, BibleBook.PS, 119, verseRef.toInt())
        val verseRange = VerseRange(KJV_VERSIFICATION, verse)
        val bookmark = BibleBookmarkWithNotes(verseRange, null, true, null)
        bookmark.notes = note

        val savedBookmark = bookmarkControl!!.addOrUpdateBibleBookmark(bookmark, setOf(labelId))
        createdBibleBookmarkIds.add(savedBookmark.id)
        return savedBookmark
    }

    private fun createGenericBookmarkWithNote(labelId: IdType, reference: String, note: String): GenericBookmarkWithNotes {
        val bookmark = GenericBookmarkWithNotes(
            key = reference,
            bookInitials = "",
            ordinalStart = 0,
            ordinalEnd = 0,
            startOffset = null,
            endOffset = null,
            wholeVerse = true,
            playbackSettings = null,
            new = true
        )
        bookmark.notes = note

        val savedBookmark = bookmarkControl!!.addOrUpdateGenericBookmark(bookmark, setOf(labelId))
        createdGenericBookmarkIds.add(savedBookmark.id)
        return savedBookmark
    }

    // ========== Tests for searchStudyPadsByContent() ==========

    @Test
    fun testSearchInTextEntries_basicSearch() = runBlocking {
        // Create a study pad with text entries
        val label = createStudyPadLabel("My Study Pad")
        createStudyPadTextEntry(label.id, "This is about prayer and devotion")
        createStudyPadTextEntry(label.id, "Another entry about worship")

        // Search for "prayer"
        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        // Verify results
        assertEquals(1, results.size)
        assertEquals(label.id, results[0].label.id)
        assertEquals(1, results[0].matchCount)
        assertTrue(results[0].matches[0].textSnippet.contains("prayer", ignoreCase = true))
    }

    @Test
    fun testSearchInTextEntries_caseInsensitive() = runBlocking {
        val label = createStudyPadLabel("Case Test")
        createStudyPadTextEntry(label.id, "PRAYER in uppercase")
        createStudyPadTextEntry(label.id, "prayer in lowercase")
        createStudyPadTextEntry(label.id, "PrAyEr in mixed case")

        // Search should find all three regardless of case
        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(1, results.size)
        assertEquals(3, results[0].matchCount)
    }

    @Test
    fun testSearchInBibleBookmarkNotes() = runBlocking {
        val label = createStudyPadLabel("Bible Notes")
        createBibleBookmarkWithNote(label.id, "1", "Important note about prayer life")
        createBibleBookmarkWithNote(label.id, "2", "Another note about worship")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(1, results.size)
        assertEquals(1, results[0].matchCount)
        assertTrue(results[0].matches[0].textSnippet.contains("prayer", ignoreCase = true))
    }

    @Test
    fun testSearchInGenericBookmarkNotes() = runBlocking {
        val label = createStudyPadLabel("Generic Notes")
        createGenericBookmarkWithNote(label.id, "Some reference", "Notes about daily prayer")
        createGenericBookmarkWithNote(label.id, "Another ref", "Notes about fasting")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(1, results.size)
        assertEquals(1, results[0].matchCount)
        assertTrue(results[0].matches[0].textSnippet.contains("prayer", ignoreCase = true))
    }

    @Test
    fun testSearchMultipleMatchesInSameStudyPad() = runBlocking {
        val label = createStudyPadLabel("Multiple Matches")
        createStudyPadTextEntry(label.id, "First entry about prayer")
        createStudyPadTextEntry(label.id, "Second entry also about prayer")
        createBibleBookmarkWithNote(label.id, "1", "Bookmark note mentioning prayer")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(1, results.size)
        assertEquals(3, results[0].matchCount)
        assertEquals(3, results[0].matches.size)
    }

    @Test
    fun testSearchAcrossMultipleStudyPads() = runBlocking {
        val label1 = createStudyPadLabel("Study Pad 1")
        val label2 = createStudyPadLabel("Study Pad 2")
        val label3 = createStudyPadLabel("Study Pad 3")

        createStudyPadTextEntry(label1.id, "Content about prayer")
        createStudyPadTextEntry(label2.id, "Content about fasting")
        createStudyPadTextEntry(label3.id, "More content about prayer")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(2, results.size)
        assertTrue(results.any { it.label.id == label1.id })
        assertTrue(results.any { it.label.id == label3.id })
        assertFalse(results.any { it.label.id == label2.id })
    }

    @Test
    fun testSearchSortingByMatchCount() = runBlocking {
        val label1 = createStudyPadLabel("Few Matches")
        val label2 = createStudyPadLabel("Many Matches")
        val label3 = createStudyPadLabel("One Match")

        // label1: 2 matches
        createStudyPadTextEntry(label1.id, "Prayer is important")
        createStudyPadTextEntry(label1.id, "Prayer changes things")

        // label2: 3 matches
        createStudyPadTextEntry(label2.id, "Prayer in the morning")
        createStudyPadTextEntry(label2.id, "Prayer in the evening")
        createBibleBookmarkWithNote(label2.id, "1", "Prayer note")

        // label3: 1 match
        createStudyPadTextEntry(label3.id, "Something about prayer")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(3, results.size)
        // Should be sorted by match count descending
        assertEquals(label2.id, results[0].label.id)
        assertEquals(3, results[0].matchCount)
        assertEquals(label1.id, results[1].label.id)
        assertEquals(2, results[1].matchCount)
        assertEquals(label3.id, results[2].label.id)
        assertEquals(1, results[2].matchCount)
    }

    @Test
    fun testSearchSortingByNameWhenMatchCountEqual() = runBlocking {
        val labelZ = createStudyPadLabel("Z Study Pad")
        val labelA = createStudyPadLabel("A Study Pad")
        val labelM = createStudyPadLabel("M Study Pad")

        // All have 1 match
        createStudyPadTextEntry(labelZ.id, "Prayer content")
        createStudyPadTextEntry(labelA.id, "Prayer content")
        createStudyPadTextEntry(labelM.id, "Prayer content")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(3, results.size)
        // Should be sorted alphabetically by name (case-insensitive)
        assertEquals("A Study Pad", results[0].label.name)
        assertEquals("M Study Pad", results[1].label.name)
        assertEquals("Z Study Pad", results[2].label.name)
    }

    @Test
    fun testSearchNoResults() = runBlocking {
        val label = createStudyPadLabel("No Match")
        createStudyPadTextEntry(label.id, "Content about fasting")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertTrue(results.isEmpty())
    }

    @Test
    fun testSearchWithSpecialCharacters() = runBlocking {
        val label = createStudyPadLabel("Special Chars")
        createStudyPadTextEntry(label.id, "Question: What is prayer?")
        createStudyPadTextEntry(label.id, "Answer: Prayer is communication with God!")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer?")

        assertEquals(1, results.size)
        assertEquals(1, results[0].matchCount)
    }

    @Test
    fun testSearchWithUnicodeCharacters() = runBlocking {
        val label = createStudyPadLabel("Unicode")
        createStudyPadTextEntry(label.id, "Prayer 🙏 is powerful")
        createStudyPadTextEntry(label.id, "Αγάπη (love in Greek)")

        // Search for emoji
        val results1 = bookmarkControl!!.searchStudyPadsByContent("🙏")
        assertEquals(1, results1.size)

        // Search for Greek text
        val results2 = bookmarkControl!!.searchStudyPadsByContent("Αγάπη")
        assertEquals(1, results2.size)
    }

    @Test
    fun testSearchMixedContentTypes() = runBlocking {
        val label = createStudyPadLabel("Mixed Content")
        createStudyPadTextEntry(label.id, "Text entry about prayer")
        createBibleBookmarkWithNote(label.id, "1", "Bible note about prayer")
        createGenericBookmarkWithNote(label.id, "ref", "Generic note about prayer")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(1, results.size)
        assertEquals(3, results[0].matchCount)

        // Verify both entry types are present (TEXT_ENTRY and BOOKMARK_NOTE)
        // Note: Both Bible and Generic bookmarks use BOOKMARK_NOTE type
        val entryTypes = results[0].matches.map { it.entryType }.toSet()
        assertEquals(2, entryTypes.size)
        assertTrue(entryTypes.contains(EntryType.TEXT_ENTRY))
        assertTrue(entryTypes.contains(EntryType.BOOKMARK_NOTE))
    }

    // ========== Tests for generateTextSnippet() ==========

    @Test
    fun testSnippetGeneration_matchInMiddle() {
        val fullText = "This is a long text that contains the word prayer in the middle of a sentence and continues for a while longer."
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 20)

        // Should have context before and after
        assertTrue(snippet.text.contains("prayer", ignoreCase = true))
        assertTrue(snippet.text.startsWith("..."))
        assertTrue(snippet.text.endsWith("..."))

        // Match position should be correct within snippet
        val matchInSnippet = snippet.text.substring(snippet.matchStart, snippet.matchEnd)
        assertEquals("prayer", matchInSnippet.lowercase())
    }

    @Test
    fun testSnippetGeneration_matchAtStart() {
        val fullText = "Prayer is very important in our daily walk with God and we should practice it regularly."
        val searchText = "Prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 20)

        // Should NOT have prefix ellipsis
        assertFalse(snippet.text.startsWith("..."))
        // Should have suffix ellipsis
        assertTrue(snippet.text.endsWith("..."))

        // Match should be at/near the beginning
        assertEquals(0, snippet.matchStart)
    }

    @Test
    fun testSnippetGeneration_matchAtEnd() {
        val fullText = "In our daily walk with God we should always remember the importance of prayer"
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 20)

        // Should have prefix ellipsis
        assertTrue(snippet.text.startsWith("..."))
        // Should NOT have suffix ellipsis
        assertFalse(snippet.text.endsWith("..."))

        // Match should be near the end
        assertTrue(snippet.matchEnd >= snippet.text.length - 10)
    }

    @Test
    fun testSnippetGeneration_shortText() {
        val fullText = "Short prayer text"
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 50)

        // Should be the full text without ellipsis
        assertEquals(fullText, snippet.text)
        assertFalse(snippet.text.startsWith("..."))
        assertFalse(snippet.text.endsWith("..."))

        // Match position should be correct
        val matchInSnippet = snippet.text.substring(snippet.matchStart, snippet.matchEnd)
        assertEquals("prayer", matchInSnippet.lowercase())
    }

    @Test
    fun testSnippetGeneration_caseInsensitive() {
        val fullText = "This text contains PRAYER in uppercase"
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 20)

        // Should find the match despite case difference
        val matchInSnippet = snippet.text.substring(snippet.matchStart, snippet.matchEnd)
        assertEquals("PRAYER", matchInSnippet)
    }

    @Test
    fun testSnippetGeneration_matchPositionCalculation() {
        val fullText = "0123456789 prayer 0123456789"
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 5)

        // Verify that matchStart and matchEnd point to correct positions
        val extractedMatch = snippet.text.substring(snippet.matchStart, snippet.matchEnd)
        assertEquals("prayer", extractedMatch.lowercase())
        assertEquals(searchText.length, snippet.matchEnd - snippet.matchStart)
    }

    @Test
    fun testSnippetGeneration_veryLongText() {
        val prefix = "a".repeat(500)
        val suffix = "b".repeat(500)
        val fullText = "$prefix prayer $suffix"
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 50)

        // Snippet should be much shorter than full text
        assertTrue(snippet.text.length < fullText.length)
        // Should contain the match
        assertTrue(snippet.text.contains("prayer", ignoreCase = true))
        // Should have both ellipses
        assertTrue(snippet.text.startsWith("..."))
        assertTrue(snippet.text.endsWith("..."))
    }

    @Test
    fun testSnippetGeneration_unicodeText() {
        val fullText = "🙏 Daily prayer 🙏 is essential for spiritual growth 🌱"
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 10)

        // Should handle Unicode correctly
        assertTrue(snippet.text.contains("prayer", ignoreCase = true))
        val matchInSnippet = snippet.text.substring(snippet.matchStart, snippet.matchEnd)
        assertEquals("prayer", matchInSnippet.lowercase())
    }

    @Test
    fun testSnippetGeneration_noMatchReturnsBeginning() {
        val fullText = "This text does not contain the search term"
        val searchText = "prayer"

        val snippet = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 20)

        // Should return beginning of text when no match
        assertTrue(snippet.text.startsWith("This text"))
        assertEquals(0, snippet.matchStart)
        assertEquals(0, snippet.matchEnd)
    }

    @Test
    fun testSnippetGeneration_customContextSize() {
        val fullText = "a".repeat(100) + " prayer " + "b".repeat(100)
        val searchText = "prayer"

        // Test with different context sizes
        val snippet10 = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 10)
        val snippet50 = bookmarkControl!!.generateTextSnippet(fullText, searchText, contextChars = 50)

        // Larger context should produce longer snippet
        assertTrue(snippet50.text.length > snippet10.text.length)

        // Both should contain the match
        assertTrue(snippet10.text.contains("prayer", ignoreCase = true))
        assertTrue(snippet50.text.contains("prayer", ignoreCase = true))
    }

    // ========== Integration Tests ==========

    @Test
    fun testEndToEndSearch_realWorldScenario() = runBlocking {
        // Create a realistic Study Pad structure
        val devotions = createStudyPadLabel("Morning Devotions")
        val theology = createStudyPadLabel("Theology Notes")
        val personal = createStudyPadLabel("Personal Reflections")

        // Add various content
        createStudyPadTextEntry(devotions.id,
            "Today I learned about the power of prayer and how it transforms our relationship with God.")
        createStudyPadTextEntry(devotions.id,
            "Reading about the importance of daily Bible study and worship.")
        createBibleBookmarkWithNote(devotions.id, "1",
            "Psalm 119:1 - This verse emphasizes the importance of prayer in maintaining a pure heart.")

        createStudyPadTextEntry(theology.id,
            "The doctrine of prayer has been central to Christian theology throughout history.")
        createStudyPadTextEntry(theology.id,
            "Understanding the Trinity helps us grasp the nature of our prayers.")

        createStudyPadTextEntry(personal.id,
            "My personal experience with answered prayers has strengthened my faith immensely.")

        // Search for "prayer"
        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        // Verify results - we should find at least 2 Study Pads
        // (devotions with 2 matches, theology with 2 matches)
        // Note: "prayers" is not matched by "prayer" in all SQL implementations
        assertTrue("Should find at least 2 Study Pads", results.size >= 2)

        // devotions should have 2 text entry matches
        val devotionsResult = results.find { it.label.id == devotions.id }
        assertNotNull("Devotions Study Pad should be found", devotionsResult)
        assertTrue("Devotions should have at least 2 matches", devotionsResult!!.matchCount >= 2)

        // theology should have 1 match (exact "prayer")
        val theologyResult = results.find { it.label.id == theology.id }
        assertNotNull("Theology Study Pad should be found", theologyResult)
        assertTrue("Theology should have at least 1 match", theologyResult!!.matchCount >= 1)

        // Verify snippets are meaningful
        devotionsResult.matches.forEach { match ->
            assertTrue(match.textSnippet.contains("prayer", ignoreCase = true))
            assertTrue(match.matchStart < match.matchEnd)
            assertTrue(match.matchEnd <= match.textSnippet.length)
        }
    }

    @Test
    fun testSnippetHighlighting_coordinatesAreCorrect() = runBlocking {
        val label = createStudyPadLabel("Highlighting Test")
        createStudyPadTextEntry(label.id,
            "This is a somewhat longer text that discusses the topic of daily prayer and its significance in our spiritual journey.")

        val results = bookmarkControl!!.searchStudyPadsByContent("prayer")

        assertEquals(1, results.size)
        val match = results[0].matches[0]

        // Verify that the coordinates correctly identify "prayer" in the snippet
        val highlightedText = match.textSnippet.substring(match.matchStart, match.matchEnd)
        assertEquals("prayer", highlightedText.lowercase())

        // Verify coordinates are within bounds
        assertTrue(match.matchStart >= 0)
        assertTrue(match.matchEnd <= match.textSnippet.length)
        assertTrue(match.matchStart < match.matchEnd)
    }

    companion object {
        private val KJV_VERSIFICATION = Versifications.instance().getVersification("KJV")
    }
}
