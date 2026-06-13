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
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.read.GetAllLabelsTool
import net.bible.service.llm.tools.read.GetBookmarksForVerseTool
import net.bible.service.llm.tools.read.GetBookmarksWithLabelTool
import net.bible.service.sword.ContentFormat
import net.bible.service.llm.tools.read.GetCommentariesTool
import net.bible.service.llm.tools.read.GetDictionaryEntryTool
import net.bible.service.llm.tools.read.GetInstalledDocumentsTool
import net.bible.service.llm.tools.read.GetStudyPadContentTool
import net.bible.service.llm.tools.read.GetVerseContentTool
import net.bible.service.llm.tools.read.SearchBibleTool
import net.bible.service.llm.tools.read.SearchStudyPadsTool
import org.json.JSONArray
import org.json.JSONObject
import net.bible.service.llm.tools.typedSuccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class ReadToolsTest {

    private val context = AgentContext(promptId = IdType())

    // === GetVerseContentTool ===

    @Test
    fun getVerseContent_missingBook() = runBlocking {
        val args = JSONObject().apply { put("verseRef", "Gen.1.1") }
        val result = GetVerseContentTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("book"))
    }

    @Test
    fun getVerseContent_missingVerseRef() = runBlocking {
        val args = JSONObject().apply { put("book", "KJV") }
        val result = GetVerseContentTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("verseRef"))
    }

    @Test
    fun getVerseContent_emptyBook() = runBlocking {
        val args = JSONObject().apply {
            put("book", "")
            put("verseRef", "Gen.1.1")
        }
        val result = GetVerseContentTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun getVerseContent_unknownBook() = runBlocking {
        val args = JSONObject().apply {
            put("book", "NONEXISTENT_BOOK_XYZ")
            put("verseRef", "Gen.1.1")
        }
        val result = GetVerseContentTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("BOOK_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun getVerseContent_formatArgsForLog() {
        val args = JSONObject().apply {
            put("book", "KJV")
            put("verseRef", "Matt.5.3")
        }
        val result = GetVerseContentTool.formatArgsForLog(args)
        assertNotNull(result)
        assertTrue(result!!.contains("KJV"))
    }

    @Test
    fun getVerseContent_formatArgsForLog_empty() {
        assertNull(GetVerseContentTool.formatArgsForLog(JSONObject()))
    }

    @Test
    fun getVerseContent_defaultFormatIsText() {
        val args = GetVerseContentTool.Args(book = "KJV", verseRef = "Gen.1.1")
        assertEquals(ContentFormat.TEXT, args.format)
    }

    @Test
    fun getVerseContent_formatXml() {
        val args = GetVerseContentTool.Args(book = "KJV", verseRef = "Gen.1.1", format = ContentFormat.XML)
        assertEquals(ContentFormat.XML, args.format)
    }

    @Test
    fun getVerseContent_requiresPermission_false() {
        assertFalse(GetVerseContentTool.requiresPermission)
    }

    // === SearchBibleTool ===

    @Test
    fun searchBible_missingQuery() = runBlocking {
        val args = JSONObject()
        val result = SearchBibleTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("query"))
    }

    @Test
    fun searchBible_emptyQuery() = runBlocking {
        val args = JSONObject().apply { put("query", "") }
        val result = SearchBibleTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun searchBible_formatArgsForLog_queryOnly() {
        val args = JSONObject().apply { put("query", "love") }
        val result = SearchBibleTool.formatArgsForLog(args)
        assertNotNull(result)
        assertTrue(result!!.contains("\"love\""))
        assertTrue(result.contains("max 50"))
    }

    @Test
    fun searchBible_formatArgsForLog_withBooks() {
        val args = JSONObject().apply {
            put("query", "love")
            put("books", JSONArray().apply { put("KJV") })
            put("maxResults", 10)
        }
        val result = SearchBibleTool.formatArgsForLog(args)
        assertNotNull(result)
        assertTrue(result!!.contains("KJV"))
        assertTrue(result.contains("max 10"))
    }

    @Test
    fun searchBible_formatArgsForLog_withOffset() {
        val args = JSONObject().apply {
            put("query", "love")
            put("offset", 100)
            put("maxResults", 25)
        }
        val result = SearchBibleTool.formatArgsForLog(args)
        assertNotNull(result)
        assertTrue(result!!.contains("offset 100"))
        assertTrue(result.contains("max 25"))
    }

    @Test
    fun searchBible_formatArgsForLog_zeroOffsetNotShown() {
        val args = JSONObject().apply {
            put("query", "love")
            put("offset", 0)
        }
        val result = SearchBibleTool.formatArgsForLog(args)
        assertNotNull(result)
        assertFalse(result!!.contains("offset"))
    }

    @Test
    fun searchBible_formatArgsForLog_empty() {
        assertNull(SearchBibleTool.formatArgsForLog(JSONObject()))
    }

    @Test
    fun searchBible_formatResultForLog_success() {
        val data = SearchBibleTool.Result(
            query = "love", totalResults = 42, returnedResults = 10,
            offset = 0, hasMore = true, results = emptyList()
        )
        val result = SearchBibleTool.formatResultForLog(typedSuccess(data))
        assertEquals("10/42 results", result)
    }

    @Test
    fun searchBible_formatResultForLog_error() {
        assertNull(SearchBibleTool.formatResultForLog(ToolResult.error("fail")))
    }

    @Test
    fun searchBible_explicitNonExistentBook_returnsNotIndexedError() = runBlocking {
        val args = JSONObject().apply {
            put("query", "love")
            put("books", JSONArray().apply { put("NONEXISTENT_BOOK") })
        }
        val result = SearchBibleTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("NOT_INDEXED", (result as ToolResult.Error).code)
        assertTrue(result.message.contains("not found"))
    }

    // === GetCommentariesTool ===

    @Test
    fun getCommentaries_missingVerseRef() = runBlocking {
        val args = JSONObject()
        val result = GetCommentariesTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("verseRef"))
    }

    @Test
    fun getCommentaries_emptyVerseRef() = runBlocking {
        val args = JSONObject().apply { put("verseRef", "") }
        val result = GetCommentariesTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun getCommentaries_formatArgsForLog() {
        val args = JSONObject().apply { put("verseRef", "Matt.5.3") }
        assertNotNull(GetCommentariesTool.formatArgsForLog(args))
    }

    @Test
    fun getCommentaries_defaultFormatIsText() {
        val args = GetCommentariesTool.Args(verseRef = "Matt.5.3")
        assertEquals(ContentFormat.TEXT, args.format)
    }

    @Test
    fun getCommentaries_formatResultForLog() {
        val data = GetCommentariesTool.Result(
            verseRef = "Matt.5.3", commentaryCount = 3, commentaries = emptyList()
        )
        assertEquals("3 commentaries", GetCommentariesTool.formatResultForLog(typedSuccess(data)))
    }

    // === GetDictionaryEntryTool ===

    @Test
    fun getDictionaryEntry_missingDictionary() = runBlocking {
        val args = JSONObject().apply { put("key", "H430") }
        val result = GetDictionaryEntryTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("dictionary"))
    }

    @Test
    fun getDictionaryEntry_missingKey() = runBlocking {
        val args = JSONObject().apply { put("dictionary", "StrongsHebrew") }
        val result = GetDictionaryEntryTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("key"))
    }

    @Test
    fun getDictionaryEntry_formatArgsForLog() {
        val args = JSONObject().apply {
            put("dictionary", "StrongsHebrew")
            put("key", "H430")
        }
        assertEquals("StrongsHebrew: H430", GetDictionaryEntryTool.formatArgsForLog(args))
    }

    @Test
    fun getDictionaryEntry_defaultFormatIsText() {
        val args = GetDictionaryEntryTool.Args(dictionary = "StrongsHebrew", key = "H430")
        assertEquals(ContentFormat.TEXT, args.format)
    }

    @Test
    fun getDictionaryEntry_formatArgsForLog_missingField() {
        assertNull(GetDictionaryEntryTool.formatArgsForLog(JSONObject().apply { put("dictionary", "StrongsHebrew") }))
    }

    // === GetBookmarksForVerseTool ===

    @Test
    fun getBookmarksForVerse_missingVerseRef() = runBlocking {
        val args = JSONObject()
        val result = GetBookmarksForVerseTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("verseRef"))
    }

    @Test
    fun getBookmarksForVerse_formatArgsForLog() {
        val args = JSONObject().apply { put("verseRef", "Rom.8.28") }
        assertNotNull(GetBookmarksForVerseTool.formatArgsForLog(args))
    }

    @Test
    fun getBookmarksForVerse_formatResultForLog() {
        val data = GetBookmarksForVerseTool.Result(
            verseRef = "Rom.8.28", bookmarkCount = 5, bookmarks = emptyList()
        )
        assertEquals("5 bookmarks", GetBookmarksForVerseTool.formatResultForLog(typedSuccess(data)))
    }

    // === GetBookmarksWithLabelTool ===

    @Test
    fun getBookmarksWithLabel_missingLabelId() = runBlocking {
        val args = JSONObject()
        val result = GetBookmarksWithLabelTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("labelId"))
    }

    @Test
    fun getBookmarksWithLabel_emptyLabelId() = runBlocking {
        val args = JSONObject().apply { put("labelId", "") }
        val result = GetBookmarksWithLabelTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun getBookmarksWithLabel_formatArgsForLog() {
        val id = IdType().toString()
        val args = JSONObject().apply { put("labelId", id) }
        val result = GetBookmarksWithLabelTool.formatArgsForLog(args)
        assertNotNull(result)
        assertTrue(result!!.length <= 12) // shortId(8) + "..."
    }

    @Test
    fun getBookmarksWithLabel_formatResultForLog() {
        val data = GetBookmarksWithLabelTool.Result(
            labelId = IdType(), labelName = "Test", bookmarkCount = 7, bookmarks = emptyList()
        )
        assertEquals("7 bookmarks", GetBookmarksWithLabelTool.formatResultForLog(typedSuccess(data)))
    }

    // === GetAllLabelsTool ===

    @Test
    fun getAllLabels_formatResultForLog() {
        val data = GetAllLabelsTool.Result(labelCount = 12, labels = emptyList())
        assertEquals("12 labels", GetAllLabelsTool.formatResultForLog(typedSuccess(data)))
    }

    @Test
    fun getAllLabels_formatResultForLog_error() {
        assertNull(GetAllLabelsTool.formatResultForLog(ToolResult.error("fail")))
    }

    @Test
    fun getAllLabels_requiresPermission_false() {
        assertFalse(GetAllLabelsTool.requiresPermission)
    }

    // === GetStudyPadContentTool ===

    @Test
    fun getStudyPadContent_missingLabelId() = runBlocking {
        val args = JSONObject()
        val result = GetStudyPadContentTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("labelId"))
    }

    @Test
    fun getStudyPadContent_emptyLabelId() = runBlocking {
        val args = JSONObject().apply { put("labelId", "") }
        val result = GetStudyPadContentTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun getStudyPadContent_formatArgsForLog() {
        val id = IdType().toString()
        val args = JSONObject().apply {
            put("labelId", id)
            put("mode", "index")
        }
        val result = GetStudyPadContentTool.formatArgsForLog(args)
        assertNotNull(result)
        assertTrue(result!!.contains("index"))
    }

    @Test
    fun getStudyPadContent_formatArgsForLog_defaultMode() {
        val id = IdType().toString()
        val args = JSONObject().apply { put("labelId", id) }
        val result = GetStudyPadContentTool.formatArgsForLog(args)
        assertNotNull(result)
        assertTrue(result!!.contains("full"))
    }

    // === SearchStudyPadsTool ===

    @Test
    fun searchStudyPads_missingQuery() = runBlocking {
        val args = JSONObject()
        val result = SearchStudyPadsTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("query"))
    }

    @Test
    fun searchStudyPads_emptyQuery() = runBlocking {
        val args = JSONObject().apply { put("query", "") }
        val result = SearchStudyPadsTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun searchStudyPads_formatArgsForLog() {
        val args = JSONObject().apply { put("query", "faith") }
        assertEquals("\"faith\"", SearchStudyPadsTool.formatArgsForLog(args))
    }

    @Test
    fun searchStudyPads_formatResultForLog() {
        val data = SearchStudyPadsTool.Result(query = "faith", studyPadCount = 2, results = emptyList())
        assertEquals("2 study pads", SearchStudyPadsTool.formatResultForLog(typedSuccess(data)))
    }

    // === GetInstalledDocumentsTool ===

    @Test
    fun getInstalledDocuments_invalidCategory() = runBlocking {
        val args = JSONObject().apply { put("category", "INVALID_CAT") }
        val result = GetInstalledDocumentsTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("INVALID_CATEGORY", (result as ToolResult.Error).code)
    }

    @Test
    fun getInstalledDocuments_formatArgsForLog_withCategory() {
        val args = JSONObject().apply { put("category", "BIBLE") }
        assertEquals("BIBLE", GetInstalledDocumentsTool.formatArgsForLog(args))
    }

    @Test
    fun getInstalledDocuments_formatArgsForLog_noCategory() {
        assertNull(GetInstalledDocumentsTool.formatArgsForLog(JSONObject()))
    }

    @Test
    fun getInstalledDocuments_formatResultForLog() {
        val data = GetInstalledDocumentsTool.Result(documentCount = 15, documents = emptyList())
        assertEquals("15 documents", GetInstalledDocumentsTool.formatResultForLog(typedSuccess(data)))
    }
}
