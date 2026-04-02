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
import net.bible.service.llm.tools.write.AddMyDocumentPageTool
import net.bible.service.llm.tools.write.CreateBookmarkTool
import net.bible.service.llm.tools.write.CreateLabelTool
import net.bible.service.llm.tools.write.CreateStudyPadTool
import net.bible.service.llm.tools.write.CreateMyDocumentTool
import net.bible.service.llm.tools.write.DeleteMyDocumentPageTool
import net.bible.service.llm.tools.write.EditMyDocumentPageTool
import net.bible.service.llm.tools.write.FinishWithMyDocumentPageTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.UpdateBookmarkNoteTool
import net.bible.service.llm.tools.read.GetMyDocumentsTool
import net.bible.service.llm.tools.read.GetMyDocumentPagesTool
import net.bible.service.sword.mydocument.MyDocumentBookManager
import net.bible.test.DatabaseResetter.resetDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

        val createData = (createResult as ToolResult.Success).data as CreateBookmarkTool.Result
        assertEquals("Gen.1.1", createData.verseRef)
        assertTrue(createData.hasNote)

        // Get bookmarks for that verse
        val getArgs = JSONObject().apply { put("verseRef", "Gen.1.1") }
        val getResult = GetBookmarksForVerseTool.execute(getArgs, context)
        assertTrue("Get should succeed", getResult is ToolResult.Success)

        val getData = (getResult as ToolResult.Success).data as GetBookmarksForVerseTool.Result
        assertTrue(getData.bookmarkCount >= 1)

        val found = getData.bookmarks.any { it.id == createData.id }
        assertTrue("Created bookmark should be found", found)
    }

    @Test
    fun createBookmark_sourcePromptIdSet() = runBlocking {
        val args = JSONObject().apply { put("verseRef", "Matt.5.3") }
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateBookmarkTool.Result

        // Verify sourcePromptId via direct DB access
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(data.id)
        assertEquals(promptId, bookmark?.sourcePromptId)
    }

    // === AddBookmarkNote ===

    @Test
    fun addBookmarkNote_success() = runBlocking {
        // Create bookmark without note
        val createArgs = JSONObject().apply { put("verseRef", "Rom.8.28") }
        val createResult = CreateBookmarkTool.execute(createArgs, context)
        val createData = (createResult as ToolResult.Success).data as CreateBookmarkTool.Result

        // Add note
        val noteArgs = JSONObject().apply {
            put("bookmarkId", createData.id.toString())
            put("note", "God works all things for good")
        }
        val noteResult = AddBookmarkNoteTool.execute(noteArgs, context)
        assertTrue(noteResult is ToolResult.Success)

        val noteData = (noteResult as ToolResult.Success).data as AddBookmarkNoteTool.Result
        assertEquals(createData.id, noteData.bookmarkId)
        assertTrue(noteData.noteLength > 0)
    }

    @Test
    fun addBookmarkNote_alreadyHasNote() = runBlocking {
        // Create bookmark with note
        val createArgs = JSONObject().apply {
            put("verseRef", "Ps.23.1")
            put("note", "Existing note")
        }
        val createResult = CreateBookmarkTool.execute(createArgs, context)
        val bookmarkId = ((createResult as ToolResult.Success).data as CreateBookmarkTool.Result).id.toString()

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
        val bookmarkId = ((createResult as ToolResult.Success).data as CreateBookmarkTool.Result).id

        // Update the note
        val updateArgs = JSONObject().apply {
            put("bookmarkId", bookmarkId.toString())
            put("note", "Updated note text")
        }
        val updateResult = UpdateBookmarkNoteTool.execute(updateArgs, context)
        assertTrue("Update should succeed", updateResult is ToolResult.Success)

        val updateData = (updateResult as ToolResult.Success).data as UpdateBookmarkNoteTool.Result
        assertEquals(bookmarkId, updateData.bookmarkId)
        assertEquals("Original note".length, updateData.previousNoteLength)
        assertEquals("Updated note text".length, updateData.noteLength)

        // Verify via DB that note was actually updated
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(bookmarkId)
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
        val beforeCount = ((beforeResult as ToolResult.Success).data as GetAllLabelsTool.Result).labelCount

        // Create label
        val createArgs = JSONObject().apply { put("name", "Test Study Label") }
        val createResult = CreateLabelTool.execute(createArgs, context)
        assertTrue("Create should succeed", createResult is ToolResult.Success)

        val createData = (createResult as ToolResult.Success).data as CreateLabelTool.Result
        assertEquals("Test Study Label", createData.name)

        // Get all labels and verify it exists
        val afterResult = GetAllLabelsTool.execute(JSONObject(), context)
        val afterData = (afterResult as ToolResult.Success).data as GetAllLabelsTool.Result
        assertEquals(beforeCount + 1, afterData.labelCount)

        val found = afterData.labels.any { it.id == createData.id }
        assertTrue("Created label should be found in getAllLabels", found)
    }

    @Test
    fun createLabel_duplicateName_getsUniqueSuffix() = runBlocking {
        val args = JSONObject().apply { put("name", "Duplicate Label") }
        val first = CreateLabelTool.execute(args, context)
        assertTrue("First create should succeed", first is ToolResult.Success)
        assertEquals("Duplicate Label", ((first as ToolResult.Success).data as CreateLabelTool.Result).name)

        val second = CreateLabelTool.execute(args, context)
        assertTrue("Duplicate should get suffix", second is ToolResult.Success)
        assertEquals("Duplicate Label (2)", ((second as ToolResult.Success).data as CreateLabelTool.Result).name)
    }

    // === AddLabelToBookmark ===

    @Test
    fun addLabelToBookmark_success() = runBlocking {
        // Create bookmark
        val bookmarkArgs = JSONObject().apply { put("verseRef", "Eph.2.8") }
        val bookmarkResult = CreateBookmarkTool.execute(bookmarkArgs, context)
        val bookmarkId = ((bookmarkResult as ToolResult.Success).data as CreateBookmarkTool.Result).id

        // Create label
        val labelArgs = JSONObject().apply { put("name", "Grace") }
        val labelResult = CreateLabelTool.execute(labelArgs, context)
        val labelId = ((labelResult as ToolResult.Success).data as CreateLabelTool.Result).id

        // Link them
        val linkArgs = JSONObject().apply {
            put("bookmarkId", bookmarkId.toString())
            put("labelId", labelId.toString())
        }
        val linkResult = AddLabelToBookmarkTool.execute(linkArgs, context)
        assertTrue("Link should succeed", linkResult is ToolResult.Success)

        val linkData = (linkResult as ToolResult.Success).data as AddLabelToBookmarkTool.Result
        assertEquals("Grace", linkData.labelName)

        // Verify via GetBookmarksWithLabel
        val queryArgs = JSONObject().apply { put("labelId", labelId.toString()) }
        val queryResult = GetBookmarksWithLabelTool.execute(queryArgs, context)
        assertTrue(queryResult is ToolResult.Success)

        val queryData = (queryResult as ToolResult.Success).data as GetBookmarksWithLabelTool.Result
        assertEquals(1, queryData.bookmarkCount)
        assertEquals("Grace", queryData.labelName)

        assertEquals(bookmarkId, queryData.bookmarks[0].id)
    }

    @Test
    fun addLabelToBookmark_alreadyLinked() = runBlocking {
        // Create bookmark and label
        val bookmarkId = ((CreateBookmarkTool.execute(
            JSONObject().apply { put("verseRef", "Phil.4.13") }, context
        ) as ToolResult.Success).data as CreateBookmarkTool.Result).id.toString()

        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Strength") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

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
        val labelId = ((labelResult as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

        // Add text entry
        val entryArgs = JSONObject().apply {
            put("labelId", labelId)
            put("text", "Paul's letter to the Romans explores justification by faith.")
            put("contentType", "MARKDOWN")
        }
        val entryResult = AddStudyPadEntryTool.execute(entryArgs, context)
        assertTrue("Entry creation should succeed", entryResult is ToolResult.Success)

        val entryData = (entryResult as ToolResult.Success).data as AddStudyPadEntryTool.Result
        assertEquals(IdType.fromString(labelId), entryData.labelId)
        assertEquals("Romans Study", entryData.labelName)
        assertTrue(entryData.textLength > 0)
        assertEquals("MARKDOWN", entryData.contentType)

        // Verify via GetStudyPadContent (full mode)
        val contentArgs = JSONObject().apply {
            put("labelId", labelId)
            put("mode", "full")
        }
        val contentResult = GetStudyPadContentTool.execute(contentArgs, context)
        assertTrue(contentResult is ToolResult.Success)

        val contentData = (contentResult as ToolResult.Success).data as GetStudyPadContentTool.EntriesResult
        assertEquals("Romans Study", contentData.labelName)
        assertEquals(1, contentData.entryCount)

        val entry = contentData.entries[0]
        assertEquals("text", entry.type)
        assertTrue(entry.text!!.contains("justification by faith"))
    }

    @Test
    fun addStudyPadEntry_infoMode() = runBlocking {
        // Create label + add entry
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Info Test") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

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

        val infoData = (infoResult as ToolResult.Success).data as GetStudyPadContentTool.InfoResult
        assertEquals(1, infoData.totalEntries)
        assertEquals(1, infoData.textEntryCount)
        assertEquals(0, infoData.bibleBookmarkCount)
        assertTrue(infoData.estimatedTextLength > 0)
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
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

        // Finish with it
        val finishArgs = JSONObject().apply {
            put("labelId", labelId)
            put("message", "Study pad ready")
        }
        val finishResult = FinishWithStudyPadTool.execute(finishArgs, context)
        assertTrue("Finish should succeed with real label", finishResult is ToolResult.Success)

        val finishData = (finishResult as ToolResult.Success).data as FinishWithStudyPadTool.Result
        assertTrue(finishData.finished)
        assertEquals(labelId, finishData.labelId)
        assertEquals("Study pad ready", finishData.message)
        assertEquals(null, finishData.scrollToEntryId)
    }

    @Test
    fun finishWithStudyPad_withScrollTo() = runBlocking {
        // Create label + add entry
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Scroll Test") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

        val entryId = ((AddStudyPadEntryTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("text", "Scroll target")
        }, context) as ToolResult.Success).data as AddStudyPadEntryTool.Result).entryId.toString()

        // Finish with scrollTo
        val finishArgs = JSONObject().apply {
            put("labelId", labelId)
            put("scrollToEntryId", entryId)
            put("message", "Scrolling to entry")
        }
        val finishResult = FinishWithStudyPadTool.execute(finishArgs, context)
        assertTrue(finishResult is ToolResult.Success)

        val finishData = (finishResult as ToolResult.Success).data as FinishWithStudyPadTool.Result
        assertEquals(entryId, finishData.scrollToEntryId)
    }

    // === Multiple StudyPad entries + index/page modes ===

    @Test
    fun studyPad_multipleEntries_indexMode() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Multi Entry Pad") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

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

        val indexData = (indexResult as ToolResult.Success).data as GetStudyPadContentTool.IndexResult
        assertEquals(3, indexData.totalEntries)
        assertEquals(3, indexData.entries.size)

        // Verify each entry has position and preview
        indexData.entries.forEachIndexed { i, entry ->
            assertEquals(i, entry.position)
            assertEquals("text", entry.type)
            assertTrue(entry.preview != null)
        }
    }

    @Test
    fun studyPad_pageMode() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Page Mode Pad") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

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

        val pageData = (pageResult as ToolResult.Success).data as GetStudyPadContentTool.PageResult
        assertEquals(5, pageData.totalEntries)
        assertEquals(1, pageData.offset)
        assertEquals(2, pageData.limit)
        assertTrue(pageData.hasMore)
        assertEquals(2, pageData.entries.size)
    }

    @Test
    fun studyPad_pageMode_lastPage() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Last Page Pad") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

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
        val pageData = (pageResult as ToolResult.Success).data as GetStudyPadContentTool.PageResult
        assertFalse(pageData.hasMore)
        assertEquals(1, pageData.entries.size)
    }

    // === GetBookmarksWithLabel fields parameter ===

    @Test
    fun getBookmarksWithLabel_fieldsParameter() = runBlocking {
        // Create label and bookmark with note
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Fields Test") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

        val bookmarkId = ((CreateBookmarkTool.execute(JSONObject().apply {
            put("verseRef", "Isa.40.31")
            put("note", "They shall mount up with wings")
            put("labelIds", org.json.JSONArray().apply { put(labelId) })
        }, context) as ToolResult.Success).data as CreateBookmarkTool.Result).id.toString()

        // Query with notes field
        val withNotes = GetBookmarksWithLabelTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("fields", org.json.JSONArray().apply {
                put("verseRange")
                put("verseName")
                put("notes")
            })
        }, context)
        val withNotesData = (withNotes as ToolResult.Success).data as GetBookmarksWithLabelTool.Result
        val bm = withNotesData.bookmarks[0]
        assertEquals("They shall mount up with wings", bm.notes)

        // Query without notes field
        val withoutNotes = GetBookmarksWithLabelTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("fields", org.json.JSONArray().apply {
                put("verseRange")
                put("verseName")
            })
        }, context)
        val withoutNotesData = (withoutNotes as ToolResult.Success).data as GetBookmarksWithLabelTool.Result
        val bm2 = withoutNotesData.bookmarks[0]
        assertNull("Notes should not be included when not in fields", bm2.notes)
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

        val bookmarkId = ((result as ToolResult.Success).data as CreateBookmarkTool.Result).id
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

        val bookmarkId = ((result as ToolResult.Success).data as CreateBookmarkTool.Result).id
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(bookmarkId)
        assertEquals("<p>Let there be light</p>", bookmark?.notes)
    }

    // === AddBookmarkNote contentType ===

    @Test
    fun addBookmarkNote_htmlContentType() = runBlocking {
        val bookmarkId = ((CreateBookmarkTool.execute(
            JSONObject().apply { put("verseRef", "Gen.1.4") }, context
        ) as ToolResult.Success).data as CreateBookmarkTool.Result).id.toString()

        val noteResult = AddBookmarkNoteTool.execute(JSONObject().apply {
            put("bookmarkId", bookmarkId)
            put("note", "<b>Bold note</b>")
            put("contentType", "HTML")
        }, context)
        assertTrue(noteResult is ToolResult.Success)

        val noteData = (noteResult as ToolResult.Success).data as AddBookmarkNoteTool.Result
        assertEquals("HTML", noteData.contentType)
    }

    @Test
    fun addBookmarkNote_notesSourcePromptIdSet() = runBlocking {
        val bookmarkId = ((CreateBookmarkTool.execute(
            JSONObject().apply { put("verseRef", "Gen.1.5") }, context
        ) as ToolResult.Success).data as CreateBookmarkTool.Result).id

        AddBookmarkNoteTool.execute(JSONObject().apply {
            put("bookmarkId", bookmarkId.toString())
            put("note", "AI-generated note")
        }, context)

        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(bookmarkId)
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
        ) as ToolResult.Success).data as CreateBookmarkTool.Result).id

        UpdateBookmarkNoteTool.execute(JSONObject().apply {
            put("bookmarkId", bookmarkId.toString())
            put("note", "Line1\\nLine2")
        }, context)

        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val bookmark = dao.bibleBookmarkById(bookmarkId)
        assertEquals("Line1\nLine2", bookmark?.notes)
    }

    // === StudyPad entry with HTML contentType ===

    @Test
    fun addStudyPadEntry_htmlContentType() = runBlocking {
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "HTML Pad") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

        val entryResult = AddStudyPadEntryTool.execute(JSONObject().apply {
            put("labelId", labelId)
            put("text", "<h1>Title</h1><p>Content</p>")
            put("contentType", "HTML")
        }, context)
        assertTrue(entryResult is ToolResult.Success)
        assertEquals("HTML", ((entryResult as ToolResult.Success).data as AddStudyPadEntryTool.Result).contentType)

        // Verify via full content
        val contentResult = GetStudyPadContentTool.execute(JSONObject().apply {
            put("labelId", labelId)
        }, context)
        val contentData = (contentResult as ToolResult.Success).data as GetStudyPadContentTool.EntriesResult
        val entry = contentData.entries[0]
        assertEquals("HTML", entry.contentType)
        assertTrue(entry.text!!.contains("<h1>Title</h1>"))
    }

    // === GetAllLabels ===

    @Test
    fun getAllLabels_returnsLabels() = runBlocking {
        val result = GetAllLabelsTool.execute(JSONObject(), context)
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as GetAllLabelsTool.Result
        assertTrue(data.labelCount >= 0)
        assertNotNull(data.labels)
    }

    // === CreateBookmark with labels ===

    @Test
    fun createBookmark_withLabels() = runBlocking {
        // Create label first
        val labelId = ((CreateLabelTool.execute(
            JSONObject().apply { put("name", "Favorites") }, context
        ) as ToolResult.Success).data as CreateLabelTool.Result).id.toString()

        // Create bookmark with label
        val args = JSONObject().apply {
            put("verseRef", "Prov.3.5")
            put("note", "Trust in the Lord")
            put("labelIds", org.json.JSONArray().apply { put(labelId) })
        }
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateBookmarkTool.Result
        assertEquals(2, data.labelCount) // user label + AI label

        // Verify bookmark appears under the label
        val queryResult = GetBookmarksWithLabelTool.execute(
            JSONObject().apply { put("labelId", labelId) }, context
        )
        val queryData = (queryResult as ToolResult.Success).data as GetBookmarksWithLabelTool.Result
        assertEquals(1, queryData.bookmarkCount)
    }

    // === MyDocuments: GetMyDocuments ===

    @Test
    fun getMyDocuments_emptyInitially() = runBlocking {
        val result = GetMyDocumentsTool.execute(JSONObject(), context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as GetMyDocumentsTool.Result
        // May have 0 or more documents depending on initial state
        assertNotNull(data.documents)
        assertEquals(data.documents.size, data.documentCount)
    }

    @Test
    fun getMyDocuments_showsAiDocument() = runBlocking {
        // Ensure AI Documents book exists
        MyDocumentBookManager.getOrCreateAIDocument()

        val result = GetMyDocumentsTool.execute(JSONObject(), context)
        val data = (result as ToolResult.Success).data as GetMyDocumentsTool.Result

        assertTrue(data.documentCount >= 1)
        assertNotNull(data.aiDocumentId)
        assertEquals("AIDocuments", data.aiDocumentInitials)

        val aiDoc = data.documents.find { it.isAIDocument }
        assertNotNull("AI Documents should be in the list", aiDoc)
        assertEquals("AIDocuments", aiDoc!!.initials)
    }

    // === MyDocuments: CreateMyDocument ===

    @Test
    fun createMyDocument_success() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Test Study Notes")
            put("description", "Notes on Romans")
        }
        val result = CreateMyDocumentTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateMyDocumentTool.Result
        assertEquals("Test Study Notes", data.name)
        assertEquals("Notes on Romans", data.description)
        assertTrue(data.initials.startsWith("MyDoc_"))

        // Verify it appears in document list
        val listResult = GetMyDocumentsTool.execute(JSONObject(), context)
        val listData = (listResult as ToolResult.Success).data as GetMyDocumentsTool.Result
        assertTrue(listData.documents.any { it.id == data.id })
    }

    @Test
    fun createMyDocument_missingName() = runBlocking {
        val result = CreateMyDocumentTool.execute(JSONObject(), context)
        assertTrue(result is ToolResult.Error)
    }

    // === MyDocuments: AddMyDocumentPage ===

    @Test
    fun addMyDocumentPage_toAiDocuments() = runBlocking {
        val args = JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Test Page")
            put("content", "# Hello World\n\nThis is a test page.")
        }
        val result = AddMyDocumentPageTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as AddMyDocumentPageTool.Result
        assertEquals("Test Page", data.title)
        assertEquals("AIDocuments", data.initials)
        assertEquals("MARKDOWN", data.contentType)
        assertTrue(data.pageKey.startsWith("page_"))
    }

    @Test
    fun addMyDocumentPage_toCustomDocument() = runBlocking {
        // Create document first
        val docResult = CreateMyDocumentTool.execute(JSONObject().apply {
            put("name", "Custom Doc")
        }, context)
        val docData = (docResult as ToolResult.Success).data as CreateMyDocumentTool.Result

        // Add page
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("documentId", docData.id.toString())
            put("title", "Chapter 1")
            put("content", "Content here")
        }, context)
        assertTrue(pageResult is ToolResult.Success)

        val pageData = (pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result
        assertEquals(docData.id, pageData.documentId)
        assertEquals("Chapter 1", pageData.title)
    }

    @Test
    fun addMyDocumentPage_normalizesLlmText() = runBlocking {
        val args = JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Normalize Test")
            put("content", "Line1\\nLine2\\tTabbed")
        }
        val result = AddMyDocumentPageTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val pageId = ((result as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        val content = dao.getContent(pageId)
        assertEquals("Line1\nLine2\tTabbed", content)
    }

    @Test
    fun addMyDocumentPage_missingTitle() = runBlocking {
        val result = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("content", "Some content")
        }, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun addMyDocumentPage_documentNotFound() = runBlocking {
        val result = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("documentId", IdType().toString())
            put("title", "Title")
            put("content", "Content")
        }, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("DOCUMENT_NOT_FOUND", (result as ToolResult.Error).code)
    }

    // === MyDocuments: GetMyDocumentPages ===

    @Test
    fun getMyDocumentPages_withoutContent() = runBlocking {
        // Create doc with pages
        val docResult = CreateMyDocumentTool.execute(JSONObject().apply {
            put("name", "Pages Test")
        }, context)
        val docData = (docResult as ToolResult.Success).data as CreateMyDocumentTool.Result

        AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("title", "Page A")
            put("content", "Content A")
        }, context)
        AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("title", "Page B")
            put("content", "Content B")
        }, context)

        // List without content
        val listResult = GetMyDocumentPagesTool.execute(JSONObject().apply {
            put("initials", docData.initials)
        }, context)
        assertTrue(listResult is ToolResult.Success)

        val listData = (listResult as ToolResult.Success).data as GetMyDocumentPagesTool.Result
        assertEquals(2, listData.pageCount)
        assertEquals(docData.initials, listData.initials)
        assertNull("Content should be null without includeContent", listData.pages[0].content)
        assertEquals("Page A", listData.pages[0].title)
        assertEquals("Page B", listData.pages[1].title)
    }

    @Test
    fun getMyDocumentPages_withContent() = runBlocking {
        val docResult = CreateMyDocumentTool.execute(JSONObject().apply {
            put("name", "Content Test")
        }, context)
        val docData = (docResult as ToolResult.Success).data as CreateMyDocumentTool.Result

        AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("title", "Page 1")
            put("content", "Hello World")
        }, context)

        val listResult = GetMyDocumentPagesTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("includeContent", true)
        }, context)
        val listData = (listResult as ToolResult.Success).data as GetMyDocumentPagesTool.Result
        assertEquals(1, listData.pageCount)
        assertEquals("Hello World", listData.pages[0].content)
    }

    @Test
    fun getMyDocumentPages_documentNotFound() = runBlocking {
        val result = GetMyDocumentPagesTool.execute(JSONObject().apply {
            put("initials", "NonExistent")
        }, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("DOCUMENT_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun getMyDocumentPages_missingIdentifier() = runBlocking {
        val result = GetMyDocumentPagesTool.execute(JSONObject(), context)
        assertTrue(result is ToolResult.Error)
        assertEquals("MISSING_IDENTIFIER", (result as ToolResult.Error).code)
    }

    // === MyDocuments: EditMyDocumentPage ===

    @Test
    fun editMyDocumentPage_updateTitle() = runBlocking {
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Original Title")
            put("content", "Content")
        }, context)
        val pageId = ((pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        val editResult = EditMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", pageId.toString())
            put("title", "Updated Title")
        }, context)
        assertTrue(editResult is ToolResult.Success)

        val editData = (editResult as ToolResult.Success).data as EditMyDocumentPageTool.Result
        assertEquals("Updated Title", editData.title)
    }

    @Test
    fun editMyDocumentPage_updateContent() = runBlocking {
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Edit Content Test")
            put("content", "Old content")
        }, context)
        val pageId = ((pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        val editResult = EditMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", pageId.toString())
            put("content", "New content")
        }, context)
        assertTrue(editResult is ToolResult.Success)

        // Verify via DB
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        assertEquals("New content", dao.getContent(pageId))
    }

    @Test
    fun editMyDocumentPage_updateOrderNumber() = runBlocking {
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Order Test")
            put("content", "Content")
        }, context)
        val pageId = ((pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        val editResult = EditMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", pageId.toString())
            put("orderNumber", 5)
        }, context)
        assertTrue(editResult is ToolResult.Success)

        val editData = (editResult as ToolResult.Success).data as EditMyDocumentPageTool.Result
        assertEquals(5, editData.orderNumber)
    }

    @Test
    fun editMyDocumentPage_pageNotFound() = runBlocking {
        val result = EditMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", IdType().toString())
            put("title", "New title")
        }, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("PAGE_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun editMyDocumentPage_nothingToUpdate() = runBlocking {
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "No Update Test")
            put("content", "Content")
        }, context)
        val pageId = ((pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        val result = EditMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", pageId.toString())
        }, context)
        assertTrue(result is ToolResult.Error)
    }

    // === MyDocuments: DeleteMyDocumentPage ===

    @Test
    fun deleteMyDocumentPage_success() = runBlocking {
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Delete Me")
            put("content", "To be deleted")
        }, context)
        val pageId = ((pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        val deleteResult = DeleteMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", pageId.toString())
        }, context)
        assertTrue(deleteResult is ToolResult.Success)

        val deleteData = (deleteResult as ToolResult.Success).data as DeleteMyDocumentPageTool.Result
        assertTrue(deleteData.deleted)
        assertEquals("Delete Me", deleteData.pageTitle)

        // Verify page is gone
        val dao = DatabaseContainer.instance.myDocumentDb.myDocumentDao()
        assertNull(dao.pageById(pageId))
    }

    @Test
    fun deleteMyDocumentPage_pageNotFound() = runBlocking {
        val result = DeleteMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", IdType().toString())
        }, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("PAGE_NOT_FOUND", (result as ToolResult.Error).code)
    }

    // === MyDocuments: Permission behavior ===

    @Test
    fun addMyDocumentPage_noPermissionForAiDocuments() {
        // Adding to AI Documents should not require permission
        val args = JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Test")
            put("content", "Content")
        }
        assertFalse(
            "AI Documents should not require permission",
            AddMyDocumentPageTool.requiresPermissionForCall(args, context)
        )
    }

    @Test
    fun addMyDocumentPage_requiresPermissionForOtherDocuments() {
        val args = JSONObject().apply {
            put("initials", "SomeOtherDoc")
            put("title", "Test")
            put("content", "Content")
        }
        assertTrue(
            "Non-AI Documents should require permission",
            AddMyDocumentPageTool.requiresPermissionForCall(args, context)
        )
    }

    @Test
    fun editMyDocumentPage_noPermissionForSessionPages() = runBlocking {
        // Add a page and simulate AgentExecutor tracking the created page ID
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Session Page")
            put("content", "Content")
        }, context)
        val pageId = ((pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        // Simulate what AgentExecutor.processToolCalls does: update context with created page ID
        val updatedContext = context.copy(createdPageIds = context.createdPageIds + pageId)

        // Edit should not require permission for pages created in this session
        val editArgs = JSONObject().apply {
            put("pageId", pageId.toString())
            put("title", "New Title")
        }
        assertFalse(
            "Session-created pages should not require permission",
            EditMyDocumentPageTool.requiresPermissionForCall(editArgs, updatedContext)
        )
    }

    @Test
    fun editMyDocumentPage_requiresPermissionForOtherPages() {
        val args = JSONObject().apply {
            put("pageId", IdType().toString())
            put("title", "New Title")
        }
        assertTrue(
            "Non-session pages should require permission",
            EditMyDocumentPageTool.requiresPermissionForCall(args, context)
        )
    }

    // === MyDocuments: Full workflow (create doc → add pages → list → edit → delete) ===

    @Test
    fun myDocuments_fullWorkflow() = runBlocking {
        // 1. Create document
        val docResult = CreateMyDocumentTool.execute(JSONObject().apply {
            put("name", "Workflow Test")
        }, context)
        val docData = (docResult as ToolResult.Success).data as CreateMyDocumentTool.Result

        // 2. Add two pages
        val page1Result = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("title", "Introduction")
            put("content", "This is the introduction.")
        }, context)
        val page1Id = ((page1Result as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        val page2Result = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("title", "Chapter 1")
            put("content", "This is chapter 1.")
        }, context)
        val page2Id = ((page2Result as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        // 3. List pages
        val listResult = GetMyDocumentPagesTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("includeContent", true)
        }, context)
        val listData = (listResult as ToolResult.Success).data as GetMyDocumentPagesTool.Result
        assertEquals(2, listData.pageCount)

        // 4. Edit first page
        EditMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", page1Id.toString())
            put("title", "Preface")
            put("content", "Updated introduction content.")
        }, context)

        // 5. Verify edit
        val verifyResult = GetMyDocumentPagesTool.execute(JSONObject().apply {
            put("initials", docData.initials)
            put("includeContent", true)
        }, context)
        val verifyData = (verifyResult as ToolResult.Success).data as GetMyDocumentPagesTool.Result
        val editedPage = verifyData.pages.find { it.id == page1Id }
        assertEquals("Preface", editedPage?.title)
        assertEquals("Updated introduction content.", editedPage?.content)

        // 6. Delete second page
        DeleteMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", page2Id.toString())
        }, context)

        // 7. Verify deletion
        val finalResult = GetMyDocumentPagesTool.execute(JSONObject().apply {
            put("initials", docData.initials)
        }, context)
        val finalData = (finalResult as ToolResult.Success).data as GetMyDocumentPagesTool.Result
        assertEquals(1, finalData.pageCount)
        assertFalse(finalData.pages.any { it.id == page2Id })
    }

    // === FinishWithMyDocumentPage ===

    @Test
    fun finishWithMyDocumentPage_success() = runBlocking {
        // Create a page first
        val pageResult = AddMyDocumentPageTool.execute(JSONObject().apply {
            put("initials", "AIDocuments")
            put("title", "Finish Test Page")
            put("content", "Some content to show")
        }, context)
        val pageId = ((pageResult as ToolResult.Success).data as AddMyDocumentPageTool.Result).pageId

        // Finish with that page
        val finishResult = FinishWithMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", pageId.toString())
            put("message", "Created and opening page")
        }, context)
        assertTrue(finishResult is ToolResult.Success)

        val data = (finishResult as ToolResult.Success).data as FinishWithMyDocumentPageTool.Result
        assertTrue(data.finished)
        assertEquals("AIDocuments", data.documentInitials)
        assertTrue(data.pageKey.isNotBlank())
        assertEquals("Created and opening page", data.message)
    }

    @Test
    fun finishWithMyDocumentPage_pageNotFound() = runBlocking {
        val result = FinishWithMyDocumentPageTool.execute(JSONObject().apply {
            put("pageId", IdType().toString())
            put("message", "Test")
        }, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("PAGE_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun finishWithMyDocumentPage_missingPageId() = runBlocking {
        val result = FinishWithMyDocumentPageTool.execute(JSONObject().apply {
            put("message", "Test")
        }, context)
        assertTrue(result is ToolResult.Error)
    }

    // === CreateStudyPad ===

    @Test
    fun createStudyPad_textAndBookmarks() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Romans Study")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "# Introduction\nPaul's letter to the Romans.")
                })
                put(JSONObject().apply {
                    put("type", "bookmark")
                    put("verseRef", "Rom.1.1")
                    put("text", "Paul introduces himself")
                })
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "## Key Theme: Justification")
                })
                put(JSONObject().apply {
                    put("type", "bookmark")
                    put("verseRef", "Rom.3.23-24")
                })
            })
        }
        val result = CreateStudyPadTool.execute(args, context)
        assertTrue("Should succeed", result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateStudyPadTool.Result
        assertEquals("Romans Study", data.labelName)
        assertEquals(4, data.itemsCreated)
        assertEquals(2, data.textEntries)
        assertEquals(2, data.bookmarkEntries)
        assertTrue(data.errors.isEmpty())

        // Verify StudyPad content via GetStudyPadContent
        val contentResult = GetStudyPadContentTool.execute(JSONObject().apply {
            put("labelId", data.labelId.toString())
        }, context)
        assertTrue(contentResult is ToolResult.Success)
        val contentData = (contentResult as ToolResult.Success).data as GetStudyPadContentTool.EntriesResult
        assertEquals(4, contentData.entries.size)

        // Verify ordering: text, bibleBookmark, text, bibleBookmark
        assertEquals("text", contentData.entries[0].type)
        assertEquals("bibleBookmark", contentData.entries[1].type)
        assertEquals("text", contentData.entries[2].type)
        assertEquals("bibleBookmark", contentData.entries[3].type)

        // Verify text content
        assertTrue(contentData.entries[0].text!!.contains("Introduction"))
        assertTrue(contentData.entries[2].text!!.contains("Justification"))

        // Verify bookmark has note
        assertEquals("Rom.1.1", contentData.entries[1].verseRange)
        assertTrue(contentData.entries[1].notes!!.contains("Paul introduces himself"))

        // Verify bookmark without note
        assertEquals("Rom.3.23-Rom.3.24", contentData.entries[3].verseRange)
        assertNull(contentData.entries[3].notes)
    }

    @Test
    fun createStudyPad_withIndentLevels() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Indented Study")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Top level heading")
                })
                put(JSONObject().apply {
                    put("type", "bookmark")
                    put("verseRef", "Gen.1.1")
                    put("indentLevel", 1)
                })
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Indented note")
                    put("indentLevel", 2)
                })
            })
        }
        val result = CreateStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateStudyPadTool.Result
        assertEquals(3, data.itemsCreated)

        // Verify indent levels via DB
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()
        val textEntries = dao.studyPadTextEntriesByLabelId(data.labelId)
        val topLevel = textEntries.find { it.text.contains("Top level") }
        val indented = textEntries.find { it.text.contains("Indented note") }
        assertEquals(0, topLevel!!.indentLevel)
        assertEquals(2, indented!!.indentLevel)

        val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(data.labelId)
        assertEquals(1, bookmarkToLabels.size)
        assertEquals(1, bookmarkToLabels[0].indentLevel)
    }

    @Test
    fun createStudyPad_emptyName() = runBlocking {
        val args = JSONObject().apply {
            put("name", "")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Content")
                })
            })
        }
        val result = CreateStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun createStudyPad_emptyItems() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Empty Study")
            put("items", org.json.JSONArray())
        }
        val result = CreateStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("EMPTY_ITEMS", (result as ToolResult.Error).code)
    }

    @Test
    fun createStudyPad_duplicateName_getsUniqueSuffix() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Duplicate Study")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Content")
                })
            })
        }
        val first = CreateStudyPadTool.execute(args, context)
        assertTrue(first is ToolResult.Success)
        assertEquals("Duplicate Study", ((first as ToolResult.Success).data as CreateStudyPadTool.Result).labelName)

        // Second gets (2) suffix
        val second = CreateStudyPadTool.execute(args, context)
        assertTrue(second is ToolResult.Success)
        assertEquals("Duplicate Study (2)", ((second as ToolResult.Success).data as CreateStudyPadTool.Result).labelName)

        // Third gets (3) suffix
        val third = CreateStudyPadTool.execute(args, context)
        assertTrue(third is ToolResult.Success)
        assertEquals("Duplicate Study (3)", ((third as ToolResult.Success).data as CreateStudyPadTool.Result).labelName)
    }

    @Test
    fun createStudyPad_invalidVerseRef_partialSuccess() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Partial Study")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Valid text entry")
                })
                put(JSONObject().apply {
                    put("type", "bookmark")
                    put("verseRef", "NotABook.99.99")
                })
                put(JSONObject().apply {
                    put("type", "bookmark")
                    put("verseRef", "John.3.16")
                })
            })
        }
        val result = CreateStudyPadTool.execute(args, context)
        assertTrue("Should succeed partially", result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateStudyPadTool.Result
        assertEquals(2, data.itemsCreated)
        assertEquals(1, data.textEntries)
        assertEquals(1, data.bookmarkEntries)
        assertEquals(1, data.errors.size)
        assertEquals(1, data.errors[0].index)
    }

    @Test
    fun createStudyPad_bookmarkMissingVerseRef() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Missing Ref Study")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "bookmark")
                    // no verseRef
                })
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Valid entry")
                })
            })
        }
        val result = CreateStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateStudyPadTool.Result
        assertEquals(1, data.itemsCreated)
        assertEquals(1, data.textEntries)
        assertEquals(0, data.bookmarkEntries)
        assertEquals(1, data.errors.size)
    }

    @Test
    fun createStudyPad_sourcePromptIdSet() = runBlocking {
        val args = JSONObject().apply {
            put("name", "Prompt Tracking Study")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "AI generated text")
                })
                put(JSONObject().apply {
                    put("type", "bookmark")
                    put("verseRef", "Ps.23.1")
                    put("text", "AI note")
                })
            })
        }
        val result = CreateStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Success)

        val data = (result as ToolResult.Success).data as CreateStudyPadTool.Result
        val dao = DatabaseContainer.instance.bookmarkDb.bookmarkDao()

        // Verify text entry has sourcePromptId
        val textEntries = dao.studyPadTextEntriesByLabelId(data.labelId)
        assertEquals(promptId, textEntries[0].sourcePromptId)

        // Verify bookmark has sourcePromptId
        val bookmarkToLabels = dao.getBookmarkToLabelsForLabel(data.labelId)
        val bookmark = dao.bibleBookmarkById(bookmarkToLabels[0].bookmarkId)
        assertEquals(promptId, bookmark?.sourcePromptId)
    }

    @Test
    fun createStudyPad_thenFinishWithStudyPad() = runBlocking {
        // Create StudyPad
        val createArgs = JSONObject().apply {
            put("name", "Finish Test Study")
            put("items", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "text")
                    put("text", "Study content")
                })
                put(JSONObject().apply {
                    put("type", "bookmark")
                    put("verseRef", "Matt.5.3")
                })
            })
        }
        val createResult = CreateStudyPadTool.execute(createArgs, context)
        val labelId = ((createResult as ToolResult.Success).data as CreateStudyPadTool.Result).labelId

        // Finish with StudyPad
        val finishArgs = JSONObject().apply {
            put("labelId", labelId.toString())
            put("message", "Study created")
        }
        val finishResult = FinishWithStudyPadTool.execute(finishArgs, context)
        assertTrue(finishResult is ToolResult.Success)

        val finishData = (finishResult as ToolResult.Success).data as FinishWithStudyPadTool.Result
        assertTrue(finishData.finished)
        assertEquals(labelId.toString(), finishData.labelId)
    }
}
