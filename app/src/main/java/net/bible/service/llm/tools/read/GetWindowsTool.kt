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

package net.bible.service.llm.tools.read

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.control.page.window.WindowLayout.WindowState
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for listing all windows in the current workspace.
 *
 * Returns information about each window including its state, document, and position.
 */
object GetWindowsTool : Tool {
    @Serializable
    data class WindowInfo(
        val id: String,
        val state: String,
        val documentInitials: String?,
        val documentName: String?,
        val documentCategory: String?,
        val currentKey: String?,
        val currentKeyName: String?,
        val isActive: Boolean,
        val isSynchronized: Boolean,
        val isPinMode: Boolean,
        val isLinksWindow: Boolean,
        val orderNumber: Int
    )

    @Serializable
    data class Result(
        val windowCount: Int,
        val activeWindowId: String,
        val windows: List<WindowInfo>
    )

    override val agentTool = AgentTool.GET_WINDOWS
    override val category = ToolCategory.WINDOWS
    override val displayNameResId = R.string.tool_get_windows

    override val description = """
        Get a list of all windows in the current workspace.
        Returns each window's state (VISIBLE or MINIMISED), the document it displays,
        its current position (verse/key), and other properties.
        Use this to understand the current workspace layout before creating or managing windows.
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties: {}
    """)

    private val windowControl get() = BibleApplication.application.applicationComponent.windowControl()

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        return BibleApplication.application.getString(R.string.tool_log_window_count, data.windowCount)
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        return try {
            val windowRepository = windowControl.windowRepository
            val activeWindowId = windowRepository.activeWindow.id

            val windows = withContext(Dispatchers.Main.immediate) {
                windowRepository.windowList
                    .filter { it.windowState != WindowState.CLOSED }
                    .mapIndexed { index, window ->
                        val page = window.pageManager.currentPage
                        val document = page.currentDocument
                        val key = page.key
                        WindowInfo(
                            id = window.id.toString(),
                            state = window.windowState.toString(),
                            documentInitials = document?.initials,
                            documentName = document?.name,
                            documentCategory = document?.bookCategory?.name,
                            currentKey = key?.osisRef,
                            currentKeyName = key?.name,
                            isActive = window.id == activeWindowId,
                            isSynchronized = window.isSynchronised,
                            isPinMode = window.isPinMode,
                            isLinksWindow = window.isLinksWindow,
                            orderNumber = index
                        )
                    }
            }

            typedSuccess(Result(
                windowCount = windows.size,
                activeWindowId = activeWindowId.toString(),
                windows = windows
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to get windows: ${e.message}", "READ_ERROR")
        }
    }
}
