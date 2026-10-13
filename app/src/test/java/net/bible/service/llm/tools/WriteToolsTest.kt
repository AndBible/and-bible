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

import kotlinx.serialization.json.*
import net.bible.service.llm.tools.*

import kotlinx.coroutines.runBlocking
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.database.IdType
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.write.AddBookmarkNoteTool
import net.bible.service.llm.tools.write.AddLabelToBookmarkTool
import net.bible.service.llm.tools.write.AddStudyPadEntryTool
import net.bible.service.llm.tools.write.CreateBookmarkTool
import net.bible.service.llm.tools.write.CreateLabelTool
import net.bible.service.llm.tools.write.DeleteBookmarkTool
import net.bible.service.llm.tools.write.DeleteLabelTool
import net.bible.service.llm.tools.write.RemoveLabelFromBookmarkTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.UpdateBookmarkNoteTool
import net.bible.service.llm.tools.typedSuccess
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class WriteToolsTest {

    private val context = AgentContext(promptId = IdType())

    // === CreateBookmarkTool ===

    @Test
    fun createBookmark_missingVerseRef() = runBlocking {
        val args = buildJsonObject {}
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("verseRef"))
    }

    @Test
    fun createBookmark_emptyVerseRef() = runBlocking {
        val args = buildJsonObject { put("verseRef", "") }
        val result = CreateBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun createBookmark_requiresPermission() {
        assertTrue(CreateBookmarkTool.requiresPermission)
    }

    @Test
    fun createBookmark_formatArgsForLog_verseOnly() {
        val args = buildJsonObject { put("verseRef", "Gen.1.1") }
        val result = CreateBookmarkTool.formatArgsForLog(args)
        assertNotNull(result)
        // Should be localized verse name, not OSIS ref
    }

    @Test
    fun createBookmark_formatArgsForLog_withNote() {
        val args = buildJsonObject {
            put("verseRef", "Gen.1.1")
            put("note", "Important verse")
        }
        val result = CreateBookmarkTool.formatArgsForLog(args)!!
        assertTrue(result.contains("+note"))
    }

    @Test
    fun createBookmark_formatArgsForLog_withLabels() {
        val args = buildJsonObject {
            put("verseRef", "Gen.1.1")
            put("labelIds", buildJsonArray {
                add(IdType().toString())
                add(IdType().toString())
            })
        }
        val result = CreateBookmarkTool.formatArgsForLog(args)!!
        assertTrue(result.contains("+2 labels"))
    }

    @Test
    fun createBookmark_formatArgsForLog_empty() {
        assertNull(CreateBookmarkTool.formatArgsForLog(buildJsonObject {}))
    }

    @Test
    fun createBookmark_formatResultForLog() {
        val data = CreateBookmarkTool.Result(
            id = IdType(), verseRef = "Gen.1.1", verseName = "Genesis 1:1",
            hasNote = false, labelCount = 0
        )
        assertEquals("Genesis 1:1", CreateBookmarkTool.formatResultForLog(typedSuccess(data)))
    }

    // === AddBookmarkNoteTool ===

    @Test
    fun addBookmarkNote_missingBookmarkId() = runBlocking {
        val args = buildJsonObject { put("note", "Some note") }
        val result = AddBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("bookmarkId"))
    }

    @Test
    fun addBookmarkNote_missingNote() = runBlocking {
        val args = buildJsonObject { put("bookmarkId", IdType().toString()) }
        val result = AddBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("note"))
    }

    @Test
    fun addBookmarkNote_emptyBookmarkId() = runBlocking {
        val args = buildJsonObject {
            put("bookmarkId", "")
            put("note", "Some note")
        }
        val result = AddBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun addBookmarkNote_emptyNote() = runBlocking {
        val args = buildJsonObject {
            put("bookmarkId", IdType().toString())
            put("note", "")
        }
        val result = AddBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun addBookmarkNote_requiresPermission() {
        assertTrue(AddBookmarkNoteTool.requiresPermission)
    }

    @Test
    fun addBookmarkNote_formatArgsForLog() {
        val id = IdType().toString()
        val args = buildJsonObject { put("bookmarkId", id) }
        val result = AddBookmarkNoteTool.formatArgsForLog(args)
        assertNotNull(result)
        assertEquals(shortId(id), result)
    }

    // === UpdateBookmarkNoteTool ===

    @Test
    fun updateBookmarkNote_missingBookmarkId() = runBlocking {
        val args = buildJsonObject { put("note", "Updated note") }
        val result = UpdateBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("bookmarkId"))
    }

    @Test
    fun updateBookmarkNote_missingNote() = runBlocking {
        val args = buildJsonObject { put("bookmarkId", IdType().toString()) }
        val result = UpdateBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("note"))
    }

    @Test
    fun updateBookmarkNote_emptyNote() = runBlocking {
        val args = buildJsonObject {
            put("bookmarkId", IdType().toString())
            put("note", "")
        }
        val result = UpdateBookmarkNoteTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun updateBookmarkNote_requiresPermission() {
        assertTrue(UpdateBookmarkNoteTool.requiresPermission)
    }

    @Test
    fun updateBookmarkNote_formatArgsForLog() {
        val id = IdType().toString()
        val args = buildJsonObject { put("bookmarkId", id) }
        assertEquals(shortId(id), UpdateBookmarkNoteTool.formatArgsForLog(args))
    }

    // === CreateLabelTool ===

    @Test
    fun createLabel_missingName() = runBlocking {
        val args = buildJsonObject {}
        val result = CreateLabelTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("name"))
    }

    @Test
    fun createLabel_emptyName() = runBlocking {
        val args = buildJsonObject { put("name", "") }
        val result = CreateLabelTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun createLabel_requiresPermission() {
        assertTrue(CreateLabelTool.requiresPermission)
    }

    @Test
    fun createLabel_formatArgsForLog() {
        val args = buildJsonObject { put("name", "My Label") }
        assertEquals("\"My Label\"", CreateLabelTool.formatArgsForLog(args))
    }

    @Test
    fun createLabel_formatArgsForLog_empty() {
        assertNull(CreateLabelTool.formatArgsForLog(buildJsonObject {}))
    }

    @Test
    fun createLabel_formatResultForLog() {
        val data = CreateLabelTool.Result(id = IdType(), name = "Study Notes", color = 0)
        assertEquals("\"Study Notes\"", CreateLabelTool.formatResultForLog(typedSuccess(data)))
    }

    // === AddLabelToBookmarkTool ===

    @Test
    fun addLabelToBookmark_missingBookmarkId() = runBlocking {
        val args = buildJsonObject { put("labelId", IdType().toString()) }
        val result = AddLabelToBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("bookmarkId"))
    }

    @Test
    fun addLabelToBookmark_missingLabelId() = runBlocking {
        val args = buildJsonObject { put("bookmarkId", IdType().toString()) }
        val result = AddLabelToBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("labelId"))
    }

    @Test
    fun addLabelToBookmark_bothEmpty() = runBlocking {
        val args = buildJsonObject {
            put("bookmarkId", "")
            put("labelId", "")
        }
        val result = AddLabelToBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun addLabelToBookmark_requiresPermission() {
        assertTrue(AddLabelToBookmarkTool.requiresPermission)
    }

    @Test
    fun addLabelToBookmark_formatArgsForLog() {
        val bId = IdType().toString()
        val lId = IdType().toString()
        val args = buildJsonObject {
            put("bookmarkId", bId)
            put("labelId", lId)
        }
        val result = AddLabelToBookmarkTool.formatArgsForLog(args)!!
        assertTrue(result.contains(shortId(bId)))
        assertTrue(result.contains(shortId(lId)))
        assertTrue(result.contains("\u2192")) // arrow
    }

    @Test
    fun addLabelToBookmark_formatArgsForLog_missingField() {
        assertNull(AddLabelToBookmarkTool.formatArgsForLog(buildJsonObject { put("bookmarkId", IdType().toString()) }))
    }

    // === AddStudyPadEntryTool ===

    @Test
    fun addStudyPadEntry_missingLabelId() = runBlocking {
        val args = buildJsonObject { put("text", "Some text") }
        val result = AddStudyPadEntryTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("labelId"))
    }

    @Test
    fun addStudyPadEntry_missingText() = runBlocking {
        val args = buildJsonObject { put("labelId", IdType().toString()) }
        val result = AddStudyPadEntryTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("text"))
    }

    @Test
    fun addStudyPadEntry_emptyText() = runBlocking {
        val args = buildJsonObject {
            put("labelId", IdType().toString())
            put("text", "")
        }
        val result = AddStudyPadEntryTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun addStudyPadEntry_requiresPermission() {
        assertTrue(AddStudyPadEntryTool.requiresPermission)
    }

    @Test
    fun addStudyPadEntry_formatArgsForLog() {
        val id = IdType().toString()
        val args = buildJsonObject {
            put("labelId", id)
            put("contentType", "HTML")
        }
        val result = AddStudyPadEntryTool.formatArgsForLog(args)!!
        assertTrue(result.contains(shortId(id)))
        assertTrue(result.contains("HTML"))
    }

    // === FinishWithStudyPadTool ===

    @Test
    fun finishWithStudyPad_missingLabelId() = runBlocking {
        val args = buildJsonObject { put("message", "Done") }
        val result = FinishWithStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("MISSING_LABEL_ID", (result as ToolResult.Error).code)
    }

    @Test
    fun finishWithStudyPad_emptyLabelId() = runBlocking {
        val args = buildJsonObject {
            put("labelId", "")
            put("message", "Done")
        }
        val result = FinishWithStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("MISSING_LABEL_ID", (result as ToolResult.Error).code)
    }

    @Test
    fun finishWithStudyPad_invalidLabelIdFormat() = runBlocking {
        val args = buildJsonObject {
            put("labelId", "not-a-valid-uuid")
            put("message", "Done")
        }
        val result = FinishWithStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("INVALID_ARGS", (result as ToolResult.Error).code)
    }

    @Test
    fun finishWithStudyPad_nonExistentLabel() = runBlocking {
        val args = buildJsonObject {
            put("labelId", IdType().toString())
            put("message", "Done")
        }
        val result = FinishWithStudyPadTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("LABEL_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun finishWithStudyPad_formatArgsForLog() {
        val id = IdType().toString()
        val args = buildJsonObject { put("labelId", id) }
        assertEquals(shortId(id), FinishWithStudyPadTool.formatArgsForLog(args))
    }

    @Test
    fun finishWithStudyPad_formatArgsForLog_empty() {
        assertNull(FinishWithStudyPadTool.formatArgsForLog(buildJsonObject {}))
    }

    // === DeleteBookmarkTool ===

    @Test
    fun deleteBookmark_missingBookmarkId() = runBlocking {
        val args = buildJsonObject {}
        val result = DeleteBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("bookmarkId"))
    }

    @Test
    fun deleteBookmark_emptyBookmarkId() = runBlocking {
        val args = buildJsonObject { put("bookmarkId", "") }
        val result = DeleteBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun deleteBookmark_nonExistentBookmark() = runBlocking {
        val args = buildJsonObject { put("bookmarkId", IdType().toString()) }
        val result = DeleteBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("BOOKMARK_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun deleteBookmark_requiresPermission() {
        assertTrue(DeleteBookmarkTool.requiresPermission)
    }

    @Test
    fun deleteBookmark_formatArgsForLog() {
        val id = IdType().toString()
        val args = buildJsonObject { put("bookmarkId", id) }
        assertEquals(shortId(id), DeleteBookmarkTool.formatArgsForLog(args))
    }

    @Test
    fun deleteBookmark_formatArgsForLog_empty() {
        assertNull(DeleteBookmarkTool.formatArgsForLog(buildJsonObject {}))
    }

    // === DeleteLabelTool ===

    @Test
    fun deleteLabel_missingLabelId() = runBlocking {
        val args = buildJsonObject {}
        val result = DeleteLabelTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("labelId"))
    }

    @Test
    fun deleteLabel_emptyLabelId() = runBlocking {
        val args = buildJsonObject { put("labelId", "") }
        val result = DeleteLabelTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun deleteLabel_nonExistentLabel() = runBlocking {
        val args = buildJsonObject { put("labelId", IdType().toString()) }
        val result = DeleteLabelTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertEquals("LABEL_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun deleteLabel_requiresPermission() {
        assertTrue(DeleteLabelTool.requiresPermission)
    }

    @Test
    fun deleteLabel_formatArgsForLog() {
        val id = IdType().toString()
        val args = buildJsonObject { put("labelId", id) }
        assertEquals(shortId(id), DeleteLabelTool.formatArgsForLog(args))
    }

    @Test
    fun deleteLabel_formatArgsForLog_empty() {
        assertNull(DeleteLabelTool.formatArgsForLog(buildJsonObject {}))
    }

    // === RemoveLabelFromBookmarkTool ===

    @Test
    fun removeLabelFromBookmark_missingBookmarkId() = runBlocking {
        val args = buildJsonObject { put("labelId", IdType().toString()) }
        val result = RemoveLabelFromBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("bookmarkId"))
    }

    @Test
    fun removeLabelFromBookmark_missingLabelId() = runBlocking {
        val args = buildJsonObject { put("bookmarkId", IdType().toString()) }
        val result = RemoveLabelFromBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
        assertTrue((result as ToolResult.Error).message.contains("labelId"))
    }

    @Test
    fun removeLabelFromBookmark_bothEmpty() = runBlocking {
        val args = buildJsonObject {
            put("bookmarkId", "")
            put("labelId", "")
        }
        val result = RemoveLabelFromBookmarkTool.execute(args, context)
        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun removeLabelFromBookmark_requiresPermission() {
        assertTrue(RemoveLabelFromBookmarkTool.requiresPermission)
    }

    @Test
    fun removeLabelFromBookmark_formatArgsForLog() {
        val bId = IdType().toString()
        val lId = IdType().toString()
        val args = buildJsonObject {
            put("bookmarkId", bId)
            put("labelId", lId)
        }
        val result = RemoveLabelFromBookmarkTool.formatArgsForLog(args)!!
        assertTrue(result.contains(shortId(bId)))
        assertTrue(result.contains(shortId(lId)))
        assertTrue(result.contains("\u2190")) // left arrow
    }

    @Test
    fun removeLabelFromBookmark_formatArgsForLog_missingField() {
        assertNull(RemoveLabelFromBookmarkTool.formatArgsForLog(buildJsonObject { put("bookmarkId", IdType().toString()) }))
    }
}
