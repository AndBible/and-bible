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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.bible.android.BibleApplication
import net.bible.android.TEST_SDK
import net.bible.android.TestBibleApplication
import net.bible.android.control.page.window.WindowControl
import net.bible.android.control.page.window.WindowRepository
import net.bible.android.database.IdType
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.read.GetWindowsTool
import net.bible.service.llm.tools.write.CreateWindowTool
import net.bible.service.llm.tools.write.ManageWindowTool
import net.bible.service.llm.tools.write.SetWindowDocumentTool
import net.bible.test.DatabaseResetter.resetDatabase
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
class WindowToolsTest {

    private lateinit var windowControl: WindowControl
    private val promptId = IdType()
    private val context = AgentContext(promptId = promptId)

    @Before
    fun setUp() {
        val app = BibleApplication.application as TestBibleApplication
        windowControl = app.applicationComponent.windowControl()
        windowControl.windowRepository = WindowRepository(CoroutineScope(Dispatchers.Main))
        windowControl.windowRepository.initialize()
    }

    @After
    fun tearDown() {
        resetDatabase()
    }

    // === GetWindowsTool ===

    @Test
    fun getWindows_returnsDefaultWindow() = runBlocking {
        val result = GetWindowsTool.execute(JSONObject(), context)

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as GetWindowsTool.Result
        assertTrue("Should have at least 1 window", data.windowCount >= 1)
        assertNotNull("Active window ID should not be null", data.activeWindowId)

        val activeWindow = data.windows.find { it.isActive }
        assertNotNull("Should have an active window", activeWindow)
        assertEquals("VISIBLE", activeWindow!!.state)
    }

    @Test
    fun getWindows_excludesClosedWindows() = runBlocking {
        val result = GetWindowsTool.execute(JSONObject(), context)

        assertTrue(result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as GetWindowsTool.Result
        for (window in data.windows) {
            assertTrue(
                "Window state should be VISIBLE or MINIMISED, not ${window.state}",
                window.state == "VISIBLE" || window.state == "MINIMISED"
            )
        }
    }

    // === CreateWindowTool ===

    @Test
    fun createWindow_withoutParams_copiesActive() = runBlocking {
        val initialResult = GetWindowsTool.execute(JSONObject(), context)
        val initialCount = ((initialResult as ToolResult.Success).data as GetWindowsTool.Result).windowCount

        val result = CreateWindowTool.execute(JSONObject(), context)

        assertTrue("Create should succeed", result is ToolResult.Success)
        val data = (result as ToolResult.Success).data as CreateWindowTool.Result
        assertNotNull("New window ID should not be null", data.windowId)

        // Verify window count increased
        val afterResult = GetWindowsTool.execute(JSONObject(), context)
        val afterCount = ((afterResult as ToolResult.Success).data as GetWindowsTool.Result).windowCount
        assertEquals("Window count should increase by 1", initialCount + 1, afterCount)
    }

    @Test
    fun createWindow_withInvalidDocument_returnsError() = runBlocking {
        val args = JSONObject().apply {
            put("documentInitials", "NONEXISTENT_DOC_12345")
        }
        val result = CreateWindowTool.execute(args, context)

        assertTrue("Should return error for invalid document", result is ToolResult.Error)
        val error = result as ToolResult.Error
        assertEquals("DOCUMENT_NOT_FOUND", error.code)
    }

    // === ManageWindowTool ===

    @Test
    fun manageWindow_minimizeAndRestore() = runBlocking {
        // Create a second window so we can minimize one
        CreateWindowTool.execute(JSONObject(), context)

        val windows = ((GetWindowsTool.execute(JSONObject(), context) as ToolResult.Success)
            .data as GetWindowsTool.Result).windows
        val visibleWindow = windows.first { it.state == "VISIBLE" }

        // Minimize
        val minimizeArgs = JSONObject().apply {
            put("windowId", visibleWindow.id)
            put("action", "MINIMIZE")
        }
        val minimizeResult = ManageWindowTool.execute(minimizeArgs, context)
        assertTrue("Minimize should succeed", minimizeResult is ToolResult.Success)
        val minimizeData = (minimizeResult as ToolResult.Success).data as ManageWindowTool.Result
        assertEquals("minimize", minimizeData.action) // camelCase from Action.camelCase
        assertEquals("MINIMISED", minimizeData.newState)

        // Restore
        val restoreArgs = JSONObject().apply {
            put("windowId", visibleWindow.id)
            put("action", "RESTORE")
        }
        val restoreResult = ManageWindowTool.execute(restoreArgs, context)
        assertTrue("Restore should succeed", restoreResult is ToolResult.Success)
        val restoreData = (restoreResult as ToolResult.Success).data as ManageWindowTool.Result
        assertEquals("restore", restoreData.action)
    }

    @Test
    fun manageWindow_closeWindow() = runBlocking {
        // Create a second window
        val createResult = CreateWindowTool.execute(JSONObject(), context)
        val newWindowId = ((createResult as ToolResult.Success).data as CreateWindowTool.Result).windowId

        val beforeCount = ((GetWindowsTool.execute(JSONObject(), context) as ToolResult.Success)
            .data as GetWindowsTool.Result).windowCount

        // Close the new window
        val closeArgs = JSONObject().apply {
            put("windowId", newWindowId)
            put("action", "CLOSE")
        }
        val closeResult = ManageWindowTool.execute(closeArgs, context)
        assertTrue("Close should succeed", closeResult is ToolResult.Success)
        val closeData = (closeResult as ToolResult.Success).data as ManageWindowTool.Result
        assertEquals("close", closeData.action)
        assertNull("Closed window should have null state", closeData.newState)

        // Verify window count decreased
        val afterCount = ((GetWindowsTool.execute(JSONObject(), context) as ToolResult.Success)
            .data as GetWindowsTool.Result).windowCount
        assertEquals("Window count should decrease by 1", beforeCount - 1, afterCount)
    }

    @Test
    fun manageWindow_cannotCloseLastWindow() = runBlocking {
        val windows = ((GetWindowsTool.execute(JSONObject(), context) as ToolResult.Success)
            .data as GetWindowsTool.Result).windows

        // If there's only one window, try to close it
        if (windows.size == 1) {
            val closeArgs = JSONObject().apply {
                put("windowId", windows[0].id)
                put("action", "CLOSE")
            }
            val result = ManageWindowTool.execute(closeArgs, context)
            assertTrue("Should return error when closing last window", result is ToolResult.Error)
            assertEquals("CANNOT_CLOSE", (result as ToolResult.Error).code)
        }
    }

    @Test
    fun manageWindow_cannotMinimizeLastVisibleWindow() = runBlocking {
        val windows = ((GetWindowsTool.execute(JSONObject(), context) as ToolResult.Success)
            .data as GetWindowsTool.Result).windows
        val visibleWindows = windows.filter { it.state == "VISIBLE" }

        // If there's only one visible window, try to minimize it
        if (visibleWindows.size == 1) {
            val minimizeArgs = JSONObject().apply {
                put("windowId", visibleWindows[0].id)
                put("action", "MINIMIZE")
            }
            val result = ManageWindowTool.execute(minimizeArgs, context)
            assertTrue("Should return error when minimizing last visible window", result is ToolResult.Error)
            assertEquals("CANNOT_MINIMIZE", (result as ToolResult.Error).code)
        }
    }

    @Test
    fun manageWindow_invalidWindowId_returnsError() = runBlocking {
        val args = JSONObject().apply {
            put("windowId", IdType().toString())
            put("action", "CLOSE")
        }
        val result = ManageWindowTool.execute(args, context)
        assertTrue("Should return error for invalid window ID", result is ToolResult.Error)
        assertEquals("WINDOW_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun manageWindow_invalidAction_returnsError() = runBlocking {
        val windows = ((GetWindowsTool.execute(JSONObject(), context) as ToolResult.Success)
            .data as GetWindowsTool.Result).windows

        val args = JSONObject().apply {
            put("windowId", windows[0].id)
            put("action", "EXPLODE")
        }
        val result = ManageWindowTool.execute(args, context)
        assertTrue("Should return error for invalid action", result is ToolResult.Error)
    }

    // === SetWindowDocumentTool ===

    @Test
    fun setWindowDocument_invalidDocument_returnsError() = runBlocking {
        val args = JSONObject().apply {
            put("documentInitials", "NONEXISTENT_DOC_12345")
        }
        val result = SetWindowDocumentTool.execute(args, context)

        assertTrue("Should return error for invalid document", result is ToolResult.Error)
        assertEquals("DOCUMENT_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun setWindowDocument_invalidWindowId_returnsError() = runBlocking {
        val args = JSONObject().apply {
            put("windowId", IdType().toString())
            put("documentInitials", "KJV")
        }
        val result = SetWindowDocumentTool.execute(args, context)

        assertTrue("Should return error for invalid window ID", result is ToolResult.Error)
        assertEquals("WINDOW_NOT_FOUND", (result as ToolResult.Error).code)
    }

    @Test
    fun setWindowDocument_missingDocumentInitials_returnsError() = runBlocking {
        val args = JSONObject().apply {
            put("documentInitials", "")
        }
        val result = SetWindowDocumentTool.execute(args, context)

        assertTrue("Should return error for missing documentInitials", result is ToolResult.Error)
    }
}
