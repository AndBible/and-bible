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

package net.bible.service.llm.tools

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.read.GetAllLabelsTool
import net.bible.service.llm.tools.read.GetBookmarksForVerseTool
import net.bible.service.llm.tools.read.GetBookmarksWithLabelTool
import net.bible.service.llm.tools.read.GetStudyPadContentTool
import net.bible.service.llm.tools.write.AddBookmarkNoteTool
import net.bible.service.llm.tools.write.AddLabelToBookmarkTool
import net.bible.service.llm.tools.write.AddStudyPadEntryTool
import net.bible.service.llm.tools.write.CreateBookmarkTool
import net.bible.service.llm.tools.write.CreateLabelTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.UpdateBookmarkNoteTool
import net.bible.test.DatabaseResetter.resetDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ToolIntegrationTest {

    private val promptId = IdType()
    private val context = AgentContext(promptId = promptId)

    @Before
    fun setUp() {
        // Ensure clean state
    }

    @After
    fun tearDown() {
        resetDatabase()
    }

    // === CreateBookmark + GetBookmarksForVerse ===

    @Test
    fun createBookmark_thenGetByVerse() = runBlocking {
        // Create bookmark
        val createArgs = JSONObject().apply {
            put("verseRef", "Gen.1.1")
            put("note", "In the beginning")
        }
        val createResult = CreateBookmarkTool.execute(createArgs, context)
        assertTrue("Create should succeed", createResult is ToolResult.Success)

        val createData = (createResult as ToolResult.Success).data as JSONObject
        assertTrue(createData.has("id"))
        assertEquals("Gen.1.1", createData.getString("verseRef"))
        assertTrue(createData.getBoolean("hasNote"))

        // Get bookmarks for that verse
        val getArgs = JSONObject().apply { put("verseRef", "Gen.1.1") }
        val getResult = GetBookmarksForVerseTool.execute(getArgs, context)
        assertTrue("Get should succeed", getResult is ToolResult.Success)

        val getData = (getResult as ToolResult.Success).data as JSONObject
        assertTrue(getData.getInt("bookmarkCount") >= 1)

        val bookmarks = getData.getJSONArray("bookmarks")
        var found = false
        for (i in 0 until bookmarks.length()) {
            val bm = bookmarks.getJSONObject(i)
            if (bm.getString("id") == createData.getString("id")) {
                found = true
                assertEquals("In the beginning", bm.getString("notes"))
            }
        }
        assertTrue("Created bookmark should be found", found)
    }

    @Test
    fun createBookmark_sourcePromptIdSet() = runBlocking {
        val args = JSONObject().apply { put("verseRef", "Matt.5.3") }
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as JSONObject
        val bookmarkId = IdType.fromString(data.getString("id"))

        // Verify sourcePromptId via direct DB access
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(bookmarkId)
        assertEquals(promptId, bookmark?.sourcePromptId)
    }

    // === AddBookmarkNote ===

    @Test
    fun addBookmarkNote_success() = runBlocking {
        // Create bookmark without note
        val createArgs = JSONObject().apply { put("verseRef", "Rom.8.28") }
        val createResult = CreateBookmarkTool.execute(createArgs, context)
        val bookmarkId = ((createResult as ToolResult.Success).data as JSONObject).getString("id")

        // Add note
        val noteArgs = JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("note", "God works all things for good")
        }
        val noteResult = AddBookmarkNoteTool.execute(noteArgs, context)
        assertTrue(noteResult is ToolResult.Success)

        val noteData = (noteResult as ToolResult.Success).data as JSONObject
        assertEquals(bookmarkId, noteData.getString("bookmarkId"))
        assertTrue(noteData.getInt("noteLength") > 0)
    }

    @Test
    fun addBookmarkNote_alreadyHasNote() = runBlocking {
        // Create bookmark with note
        val createArgs = JSONObject().apply {
            put("verseRef", "Ps.23.1")
            put("note", "Existing note")
        }
        val createResult = CreateBookmarkTool.execute(createArgs, context)
        val bookmarkId = ((createResult as ToolResult.Success).data as JSONObject).getString("id")

        // Try to add another note
        val noteArgs = JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("note", "New note")
        }
        val noteResult = AddBookmarkNoteTool.execute(noteArgs, context)
        assertTrue(noteResult is ToolResult.Error)
        assertEquals("NOTE_EXISTS", (noteResult as ToolResult.Error).code)
    }

    @Test
    fun addBookmarkNote_nonExistentBookmark() = runBlocking {
        val args = JSONObject().apply {
            put("bookmarkId", IdType().toString())
            put("note", "Note for missing bookmark")
        }
        val result = AddBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("BOOKMARK_NOT_FOUND", (result as ToolResult.Error).code)
    }

    // === UpdateBookmarkNote ===

    @Test
    fun updateBookmarkNote_success() = runBlocking {
        // Create bookmark with note
        val createArgs = JSONObject().apply {
            put("verseRef", "John.3.16")
            put("note", "Original note")
        }
        val createResult = CreateBookmarkTool.execute(createArgs, context)
        val bookmarkId = ((createResult as ToolResult.Success).data as JSONObject).getString("id")

        // Update the note
        val updateArgs = JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("note", "Updated note text")
        }
        val updateResult = UpdateBookmarkNoteTool.execute(updateArgs, context)
        assertTrue("Update should succeed", updateResult is ToolResult.Success)

        val updateData = (updateResult as ToolResult.Success).data as JSONObject
        assertEquals(bookmarkId, updateData.getString("bookmarkId"))
        assertEquals("Original note".length, updateData.getInt("previousNoteLength"))
        assertEquals("Updated note text".length, updateData.getInt("noteLength"))

        // Verify via DB that note was actually updated
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(IdType.fromString(bookmarkId))
        assertEquals("Updated note text", bookmark?.notes)
    }

    @Test
    fun updateBookmarkNote_nonExistentBookmark() = runBlocking {
        val args = JSONObject().apply {
            put("bookmarkId", IdType().toString())
            put("note", "Note for missing bookmark")
        }
        val result = UpdateBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("BOOKMARK_NOT_FOUND", (result as ToolResult.Error).code)
    }

    // === CreateLabel + GetAllLabels ===

    @Test
    fun createLabel_thenGetAll() = runBlocking {
        // Count labels before
        val beforeResult = GetAllLabelsTool.execute(JSONObject(), context)
        val beforeCount = ((beforeResult as ToolResult.Success).data as JSONObject).getInt("labelCount")

        // Create label
        val createArgs = JSONObject().apply { put("name", "Test Study Label") }
        val createResult = CreateLabelTool.execute(createArgs, context)
        assertTrue("Create should succeed", createResult is ToolResult.Success)

        val createData = (createResult as ToolResult.Success).data as JSONObject
        assertTrue(createData.has("id"))
        assertEquals("Test Study Label", createData.getString("name"))

        // Get all labels and verify it exists
        val afterResult = GetAllLabelsTool.execute(JSONObject(), context)
        val afterData = (afterResult as ToolResult.Success).data as JSONObject
        assertEquals(beforeCount + 1, afterData.getInt("labelCount"))

        val labels = afterData.getJSONArray("labels")
        var found = false
        for (i in 0 until labels.length()) {
            val label = labels.getJSONObject(i)
            if (label.getString("id") == createData.getString("id")) {
                found = true
                assertEquals("Test Study Label", label.getString("name"))
            }
        }
        assertTrue("Created label should be found in getAllLabels", found)
    }

    @Test
    fun createLabel_duplicateName() = runBlocking {
        val args = JSONObject().apply { put("name", "Duplicate Label") }
        val first = CreateLabelTool.execute(args, context)
        assertTrue("First create should succeed", first is ToolResult.Success)

        val second = CreateLabelTool.execute(args, context)
        assertTrue("Duplicate should fail", second is ToolResult.Error)
        assertEquals("LABEL_EXISTS", (second as ToolResult.Error).code)
    }

    // === AddLabelToBookmark ===

    @Test
    fun addLabelToBookmark_success() = runBlocking {
        // Create bookmark
        val bookmarkArgs = JSONObject().apply { put("verseRef", "Eph.2.8") }
        val bookmarkResult = CreateBookmarkTool.execute(bookmarkArgs, context)
        val bookmarkId = ((bookmarkResult as ToolResult.Success).data as JSONObject).getString("id")

        // Create label
        val labelArgs = JSONObject().apply { put("name", "Grace") }
        val labelResult = CreateLabelTool.execute(labelArgs, context)
        val labelId = ((labelResult as ToolResult.Success).data as JSONObject).getString("id")

        // Link them
        val linkArgs = JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("labelId", labelId)
        }
        val linkResult = AddLabelToBookmarkTool.execute(linkArgs, context)
        assertTrue("Link should succeed", linkResult is ToolResult.Success)

        val linkData = (linkResult as ToolResult.Success).data as JSONObject
        assertEquals("Grace", linkData.getString("labelName"))

        // Verify via GetBookmarksWithLabel
        val queryArgs = JSONObject().apply { put("labelId", labelId) }
        val queryResult = GetBookmarksWithLabelTool.execute(queryArgs, context)
        assertTrue(queryResult is ToolResult.Success)

        val queryData = (queryResult as ToolResult.Success).data as JSONObject
        assertEquals(1, queryData.getInt("bookmarkCount"))
        assertEquals("Grace", queryData.getString("labelName"))

        val bookmarks = queryData.getJSONArray("bookmarks")
        assertEquals(bookmarkId, bookmarks.getJSONObject(0).getString("id"))
    }

    @Test
    fun addLabelToBookmark_alreadyLinked() = runBlocking {
        // Create bookmark and label
        val bookmarkId = ((CreateBookmarkTool.execute(
            JSONObject().apply { put("verseRef", "Phil.4.13") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Strength") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        // Link once
        val linkArgs = JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("labelId", labelId)
        }
        AddLabelToBookmarkTool.execute(linkArgs, context)

        // Try to link again
        val secondResult = AddLabelToBookmarkTool.execute(linkArgs, context)
        assertTrue(secondResult is ToolResult.Error)
        assertEquals("ALREADY_LINKED", (secondResult as ToolResult.Error).code)
    }

    // === AddStudyPadEntry + GetStudyPadContent ===

    @Test
    fun addStudyPadEntry_thenGetContent() = runBlocking {
        // Create label (which acts as StudyPad)
        val labelArgs = JSONObject().apply { put("name", "Romans Study") }
        val labelResult = CreateLabelTool.execute(labelArgs, context)
        val labelId = ((labelResult as ToolResult.Success).data as JSONObject).getString("id")

        // Add text entry
        val entryArgs = JSONObject().apply {
            put("labelId", labelId)
            put("text", "Paul's letter to the Romans explores justification by faith.")
            put("contentType", "MARKDOWN")
        }
        val entryResult = AddStudyPadEntryTool.execute(entryArgs, context)
        assertTrue("Entry creation should succeed", entryResult is ToolResult.Success)

        val entryData = (entryResult as ToolResult.Success).data as JSONObject
        assertTrue(entryData.has("entryId"))
        assertEquals(labelId, entryData.getString("labelId"))
        assertEquals("Romans Study", entryData.getString("labelName"))
        assertTrue(entryData.getInt("textLength") > 0)
        assertEquals("MARKDOWN", entryData.getString("contentType"))

        // Verify via GetStudyPadContent (full mode)
        val contentArgs = JSONObject().apply {
            put("labelId", labelId)
            put("mode", "full")
        }
        val contentResult = GetStudyPadContentTool.execute(contentArgs, context)
        assertTrue(contentResult is ToolResult.Success)

        val contentData = (contentResult as ToolResult.Success).data as JSONObject
        assertEquals("Romans Study", contentData.getString("labelName"))
        assertEquals(1, contentData.getInt("entryCount"))

        val entries = contentData.getJSONArray("entries")
        val entry = entries.getJSONObject(0)
        assertEquals("text", entry.getString("type"))
        assertTrue(entry.getString("text").contains("justification by faith"))
    }

    @Test
    fun addStudyPadEntry_infoMode() = runBlocking {
        // Create label + add entry
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Info Test") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        AddStudyPadEntryTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("text", "Some study content here")
        }, context)

        // Query in info mode
        val infoArgs = JSONObject().apply {
            put("labelId", labelId)
            put("mode", "info")
        }
        val infoResult = GetStudyPadContentTool.execute(infoArgs, context)
        assertTrue(infoResult is ToolResult.Success)

        val infoData = (infoResult as ToolResult.Success).data as JSONObject
        assertEquals(1, infoData.getInt("totalEntries"))
        assertEquals(1, infoData.getInt("textEntryCount"))
        assertEquals(0, infoData.getInt("bibleBookmarkCount"))
        assertTrue(infoData.getInt("estimatedTextLength") > 0)
    }

    @Test
    fun addStudyPadEntry_nonExistentLabel() = runBlocking {
        val args = JSONObject().apply {
            put("labelId", IdType().toString())
            put("text", "Some text")
        }
        val result = AddStudyPadEntryTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("LABEL_NOT_FOUND", (result as ToolResult.Error).code)
    }

    // === FinishWithStudyPad with real label ===

    @Test
    fun finishWithStudyPad_withRealLabel() = runBlocking {
        // Create label
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Finish Test Pad") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        // Finish with it
        val finishArgs = JSONObject().apply {
            put("labelId", labelId)
            put("message", "Study pad ready")
        }
        val finishResult = FinishWithStudyPadTool.execute(finishArgs, context)
        assertTrue("Finish should succeed with real label", finishResult is ToolResult.Success)

        val finishData = (finishResult as ToolResult.Success).data as JSONObject
        assertTrue(finishData.getBoolean("finished"))
        assertEquals(labelId, finishData.getString("labelId"))
        assertEquals("Study pad ready", finishData.getString("message"))
        assertFalse(finishData.has("scrollToEntryId"))
    }

    @Test
    fun finishWithStudyPad_withScrollTo() = runBlocking {
        // Create label + add entry
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Scroll Test") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        val entryId = ((AddStudyPadEntryTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("text", "Scroll target")
        }, context) as ToolResult.Success).data as JSONObject).getString("entryId")

        // Finish with scrollTo
        val finishArgs = JSONObject().apply {
            put("labelId", labelId)
            put("scrollToEntryId", entryId)
            put("message", "Scrolling to entry")
        }
        val finishResult = FinishWithStudyPadTool.execute(finishArgs, context)
        assertTrue(finishResult is ToolResult.Success)

        val finishData = (finishResult as ToolResult.Success).data as JSONObject
        assertEquals(entryId, finishData.getString("scrollToEntryId"))
    }

    // === Multiple StudyPad entries + index/page modes ===

    @Test
    fun studyPad_multipleEntries_indexMode() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Multi Entry Pad") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        // Add 3 entries
        for (i in 1..3) {
            AddStudyPadEntryTool.execute(JSONObject().apply {
                put("labelId", labelId)
                put("text", "Entry number $i with some content")
            }, context)
        }

        // Query in index mode
        val indexResult = GetStudyPadContentTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("mode", "index")
        }, context)
        assertTrue(indexResult is ToolResult.Success)

        val indexData = (indexResult as ToolResult.Success).data as JSONObject
        assertEquals(3, indexData.getInt("totalEntries"))
        val entries = indexData.getJSONArray("entries")
        assertEquals(3, entries.length())

        // Verify each entry has position and preview
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            assertEquals(i, entry.getInt("position"))
            assertEquals("text", entry.getString("type"))
            assertTrue(entry.has("preview"))
        }
    }

    @Test
    fun studyPad_pageMode() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Page Mode Pad") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        // Add 5 entries
        for (i in 1..5) {
            AddStudyPadEntryTool.execute(JSONObject().apply {
                put("labelId", labelId)
                put("text", "Page entry $i")
            }, context)
        }

        // Get page with offset=1, limit=2
        val pageResult = GetStudyPadContentTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("mode", "page")
            put("offset", 1)
            put("limit", 2)
        }, context)
        assertTrue(pageResult is ToolResult.Success)

        val pageData = (pageResult as ToolResult.Success).data as JSONObject
        assertEquals(5, pageData.getInt("totalEntries"))
        assertEquals(1, pageData.getInt("offset"))
        assertEquals(2, pageData.getInt("limit"))
        assertTrue(pageData.getBoolean("hasMore"))
        assertEquals(2, pageData.getJSONArray("entries").length())
    }

    @Test
    fun studyPad_pageMode_lastPage() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Last Page Pad") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        for (i in 1..3) {
            AddStudyPadEntryTool.execute(JSONObject().apply {
                put("labelId", labelId)
                put("text", "Entry $i")
            }, context)
        }

        // Get last page
        val pageResult = GetStudyPadContentTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("mode", "page")
            put("offset", 2)
            put("limit", 20)
        }, context)
        val pageData = ((pageResult as ToolResult.Success).data as JSONObject)
        assertFalse(pageData.getBoolean("hasMore"))
        assertEquals(1, pageData.getJSONArray("entries").length())
    }

    // === GetBookmarksWithLabel fields parameter ===

    @Test
    fun getBookmarksWithLabel_fieldsParameter() = runBlocking {
        // Create label and bookmark with note
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Fields Test") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        val bookmarkId = ((CreateBookmarkTool.execute(JSONObject().apply {
            put("verseRef", "Isa.40.31")
            put("note", "They shall mount up with wings")
            put("labelIds", org.json.JSONArray().apply { put(labelId) })
        }, context) as ToolResult.Success).data as JSONObject).getString("id")

        // Query with notes field
        val withNotes = GetBookmarksWithLabelTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("fields", org.json.JSONArray().apply {
                put("verseRange")
                put("verseName")
                put("notes")
            })
        }, context)
        val withNotesData = ((withNotes as ToolResult.Success).data as JSONObject)
        val bm = withNotesData.getJSONArray("bookmarks").getJSONObject(0)
        assertTrue(bm.has("notes"))
        assertEquals("They shall mount up with wings", bm.getString("notes"))

        // Query without notes field
        val withoutNotes = GetBookmarksWithLabelTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("fields", org.json.JSONArray().apply {
                put("verseRange")
                put("verseName")
            })
        }, context)
        val withoutNotesData = ((withoutNotes as ToolResult.Success).data as JSONObject)
        val bm2 = withoutNotesData.getJSONArray("bookmarks").getJSONObject(0)
        assertFalse("Notes should not be included when not in fields", bm2.has("notes"))
    }

    @Test
    fun getBookmarksWithLabel_nonExistentLabel() = runBlocking {
        val result = GetBookmarksWithLabelTool.execute(JSONObject().apply {
            put("labelId", IdType().toString())
        }, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("LABEL_NOT_FOUND", (result as ToolResult.Error).code)
    }

    // === CreateBookmark noteContentType and LLM text normalization ===

    @Test
    fun createBookmark_noteWithEscapedNewlines() = runBlocking {
        val args = JSONObject().apply {
            put("verseRef", "Gen.1.2")
            put("note", "Line one\\nLine two\\tTabbed")
        }
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val bookmarkId = IdType.fromString(((result as ToolResult.Success).data as JSONObject).getString("id"))
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(bookmarkId)
        // normalizeLlmText should have converted \\n to \n
        assertEquals("Line one\nLine two\tTabbed", bookmark?.notes)
    }

    @Test
    fun createBookmark_htmlContentType() = runBlocking {
        val args = JSONObject().apply {
            put("verseRef", "Gen.1.3")
            put("note", "<p>Let there be light</p>")
            put("noteContentType", "HTML")
        }
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val bookmarkId = IdType.fromString(((result as ToolResult.Success).data as JSONObject).getString("id"))
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(bookmarkId)
        assertEquals("<p>Let there be light</p>", bookmark?.notes)
    }

    // === AddBookmarkNote contentType ===

    @Test
    fun addBookmarkNote_htmlContentType() = runBlocking {
        val bookmarkId = ((CreateBookmarkTool.execute(
            JSONObject().apply { put("verseRef", "Gen.1.4") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        val noteResult = AddBookmarkNoteTool.execute(JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("note", "<b>Bold note</b>")
            put("contentType", "HTML")
        }, context)
        assertTrue(noteResult is ToolResult.Success)

        val noteData = (noteResult as ToolResult.Success).data as JSONObject
        assertEquals("HTML", noteData.getString("contentType"))
    }

    @Test
    fun addBookmarkNote_notesSourcePromptIdSet() = runBlocking {
        val bookmarkId = ((CreateBookmarkTool.execute(
            JSONObject().apply { put("verseRef", "Gen.1.5") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        AddBookmarkNoteTool.execute(JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("note", "AI-generated note")
        }, context)

        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(IdType.fromString(bookmarkId))
        assertEquals(promptId, bookmark?.notesSourcePromptId)
    }

    // === UpdateBookmarkNote also normalizes LLM text ===

    @Test
    fun updateBookmarkNote_normalizesLlmText() = runBlocking {
        val bookmarkId = ((CreateBookmarkTool.execute(
            JSONObject().apply {
                put("verseRef", "Gen.1.6")
                put("note", "old")
            }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        UpdateBookmarkNoteTool.execute(JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("note", "Line1\\nLine2")
        }, context)

        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(IdType.fromString(bookmarkId))
        assertEquals("Line1\nLine2", bookmark?.notes)
    }

    // === StudyPad entry with HTML contentType ===

    @Test
    fun addStudyPadEntry_htmlContentType() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "HTML Pad") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        val entryResult = AddStudyPadEntryTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("text", "<h1>Title</h1><p>Content</p>")
            put("contentType", "HTML")
        }, context)
        assertTrue(entryResult is ToolResult.Success)
        assertEquals("HTML", ((entryResult as ToolResult.Success).data as JSONObject).getString("contentType"))

        // Verify via full content
        val contentResult = GetStudyPadContentTool.execute(JSONObject().apply {
            put("labelId", labelId)
        }, context)
        val entry = ((contentResult as ToolResult.Success).data as JSONObject)
            .getJSONArray("entries").getJSONObject(0)
        assertEquals("HTML", entry.getString("contentType"))
        assertTrue(entry.getString("text").contains("<h1>Title</h1>"))
    }

    // === GetAllLabels ===

    @Test
    fun getAllLabels_returnsLabels() = runBlocking {
        val result = GetAllLabelsTool.execute(JSONObject(), context)
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as JSONObject
        assertTrue(data.has("labelCount"))
        assertTrue(data.has("labels"))
    }

    // === CreateBookmark with labels ===

    @Test
    fun createBookmark_withLabels() = runBlocking {
        // Create label first
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Favorites") }, context
        ) as ToolResult.Success).data as JSONObject).getString("id")

        // Create bookmark with label
        val args = JSONObject().apply {
            put("verseRef", "Prov.3.5")
            put("note", "Trust in the Lord")
            put("labelIds", org.json.JSONArray().apply { put(labelId) })
        }
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as JSONObject
        assertEquals(1, data.getInt("labelCount"))

        // Verify bookmark appears under the label
        val queryResult = GetBookmarksWithLabelTool.execute(
            JSONObject().apply { put("labelId", labelId) }, context
        )
        val queryData = ((queryResult as ToolResult.Success).data as JSONObject)
        assertEquals(1, queryData.getInt("bookmarkCount"))
    }
}
