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
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import net.bible.service.llm.tools.write.SetDocumentTitleTool
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class, sdk = [TEST_SDK])
class SimpleToolsTest {

    private val context = AgentContext(promptId = IdType())

    // === SetDocumentTitleTool ===

    @Test
    fun setDocumentTitle_normalTitle() = runBlocking {
        val args = JSONObject().apply { put("title", "Romans 8:28 Analysis") }
        val result = SetDocumentTitleTool.execute(args, context)

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as SetDocumentTitleTool.Result
        assertEquals("Romans 8:28 Analysis", data.title)
        assertTrue(data.finished)
    }

    @Test
    fun setDocumentTitle_emptyTitle() = runBlocking {
        val args = JSONObject().apply { put("title", "") }
        val result = SetDocumentTitleTool.execute(args, context)

        assertTrue(result is ToolResult.Error)
        assertEquals("MISSING_TITLE", (result as ToolResult.Error).code)
    }

    @Test
    fun setDocumentTitle_blankTitle() = runBlocking {
        val args = JSONObject().apply { put("title", "   ") }
        val result = SetDocumentTitleTool.execute(args, context)

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun setDocumentTitle_markdownStripped() = runBlocking {
        val args = JSONObject().apply {
            put("title", "**[Romans 8:28](sword://KJV/Rom.8.28)** - _Analysis_")
        }
        val result = SetDocumentTitleTool.execute(args, context)

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as SetDocumentTitleTool.Result
        assertEquals("Romans 8:28 - Analysis", data.title)
    }

    @Test
    fun setDocumentTitle_longTitle_truncated() = runBlocking {
        val longTitle = "A".repeat(100)
        val args = JSONObject().apply { put("title", longTitle) }
        val result = SetDocumentTitleTool.execute(args, context)

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as SetDocumentTitleTool.Result
        assertEquals(80, data.title.length)
    }

    @Test
    fun setDocumentTitle_missingParameter() = runBlocking {
        val args = JSONObject()
        val result = SetDocumentTitleTool.execute(args, context)

        assertTrue(result is ToolResult.Error)
    }

    @Test
    fun setDocumentTitle_formatArgsForLog() {
        val args = JSONObject().apply { put("title", "My Title") }
        assertEquals("\"My Title\"", SetDocumentTitleTool.formatArgsForLog(args))
    }

    @Test
    fun setDocumentTitle_formatArgsForLog_empty() {
        val args = JSONObject()
        assertEquals(null, SetDocumentTitleTool.formatArgsForLog(args))
    }

    // === FinishWithoutDocumentTool ===

    @Test
    fun finishWithoutDocument_normalMessage() = runBlocking {
        val args = JSONObject().apply { put("message", "Bookmark created successfully") }
        val result = FinishWithoutDocumentTool.execute(args, context)

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as FinishWithoutDocumentTool.Result
        assertTrue(data.finished)
        assertEquals("Bookmark created successfully", data.message)
        assertEquals(FinishWithoutDocumentTool.FINISH_WITHOUT_DOCUMENT_MARKER, data.marker)
    }

    @Test
    fun finishWithoutDocument_emptyMessage_usesDefault() = runBlocking {
        val args = JSONObject()
        val result = FinishWithoutDocumentTool.execute(args, context)

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as FinishWithoutDocumentTool.Result
        assertEquals("Task completed", data.message)
    }

    @Test
    fun finishWithoutDocument_formatArgsForLog_shortMessage() {
        val args = JSONObject().apply { put("message", "Done") }
        assertEquals("\"Done\"", FinishWithoutDocumentTool.formatArgsForLog(args))
    }

    @Test
    fun finishWithoutDocument_formatArgsForLog_longMessage() {
        val longMsg = "A".repeat(100)
        val args = JSONObject().apply { put("message", longMsg) }
        val result = FinishWithoutDocumentTool.formatArgsForLog(args)!!
        assertTrue(result.contains("..."))
        assertTrue(result.length < 100)
    }

    @Test
    fun finishWithoutDocument_formatArgsForLog_empty() {
        val args = JSONObject()
        assertEquals(null, FinishWithoutDocumentTool.formatArgsForLog(args))
    }
}
