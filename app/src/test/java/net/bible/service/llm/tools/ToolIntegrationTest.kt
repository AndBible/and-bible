/*
 * Copyright (c) 2026 Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.service.llm.tools.write.AddBookmarkNoteTool
import net.bible.service.llm.tools.write.CreateBookmarkTool
import net.bible.test.DatabaseResetter.resetDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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

    // === GetAllLabels ===

    @Test
    fun getAllLabels_returnsLabels() = runBlocking {
        val result = GetAllLabelsTool.execute(JSONObject(), context)
        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as JSONObject
        assertTrue(data.has("labelCount"))
        assertTrue(data.has("labels"))
    }
}
