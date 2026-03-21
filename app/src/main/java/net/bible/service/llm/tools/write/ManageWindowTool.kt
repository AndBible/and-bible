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

package net.bible.service.llm.tools.write

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.IdType
import net.bible.service.llm.AgentTool
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.agent.AgentContext
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolResult
import net.bible.service.llm.tools.decodeArgs
import net.bible.service.llm.tools.shortId
import net.bible.service.llm.tools.typedSuccess
import net.bible.service.llm.tools.yamlToJson
import org.json.JSONObject

/**
 * Tool for managing window state: close, minimize, or restore.
 *
 * Combines three related window operations into a single tool with an action parameter.
 */
object ManageWindowTool : Tool {
    @Serializable
    enum class Action {
        CLOSE, MINIMIZE, RESTORE;

        val camelCase: String get() = name.lowercase()
    }

    @Serializable
    data class Args(
        val windowId: String = "",
        val action: Action? = null
    )

    @Serializable
    data class Result(
        val windowId: String,
        val action: String,
        val newState: String?,
        val message: String
    )

    override val agentTool = AgentTool.MANAGE_WINDOW
    override val category = ToolCategory.WINDOWS
    override val requiresPermission = true
    override val displayNameResId = R.string.tool_manage_window

    override val description = """
        Manage a window: close, minimize, or restore it.
        Use getWindows first to get window IDs and their current states.
        - CLOSE: Permanently remove a window (cannot close the last window)
        - MINIMIZE: Hide a window without removing it (cannot minimize the last visible window)
        - RESTORE: Make a minimized window visible again
    """.trimIndent()

    override val parametersSchema = yamlToJson("""
        type: object
        properties:
          windowId:
            type: string
            description: "The window ID to act on. Get IDs from getWindows."
          action:
            type: string
            enum: [CLOSE, MINIMIZE, RESTORE]
            description: "Action: CLOSE (remove), MINIMIZE (hide), or RESTORE (show)."
        required: [windowId, action]
    """)

    private val windowControl get() = BibleApplication.application.applicationComponent.windowControl()

    override suspend fun formatActionDescription(arguments: JSONObject): String? {
        val app = BibleApplication.application
        val action = try {
            Action.valueOf(arguments.optString("action", "").uppercase())
        } catch (_: Exception) { return null }
        val windowId = arguments.optString("windowId", "")
        val window = try {
            windowControl.windowRepository.getWindow(IdType(windowId))
        } catch (_: Exception) { null }
        val docName = window?.pageManager?.currentPage?.currentDocument?.abbreviation ?: shortId(windowId)
        return when (action) {
            Action.CLOSE -> app.getString(R.string.action_close_window, docName)
            Action.MINIMIZE -> app.getString(R.string.action_minimize_window, docName)
            Action.RESTORE -> app.getString(R.string.action_restore_window, docName)
        }
    }

    override fun formatArgsForLog(arguments: JSONObject): String? {
        val action = arguments.optString("action", "").takeIf { it.isNotBlank() } ?: return null
        val windowId = arguments.optString("windowId", "")
        return "$action ${shortId(windowId)}"
    }

    override fun formatResultForLog(result: ToolResult): String? {
        if (result !is ToolResult.Success || result.data !is Result) return null
        val data = result.data as Result
        val stateStr = data.newState ?: BibleApplication.application.getString(R.string.tool_log_window_removed)
        return "${data.action} → $stateStr"
    }

    override suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult {
        val args = try {
            arguments.decodeArgs<Args>()
        } catch (e: Exception) {
            return ToolResult.error("Invalid arguments: ${e.message}", "INVALID_ARGS")
        }

        if (args.windowId.isBlank()) {
            return ToolResult.error("Missing required parameter: windowId")
        }
        val action = args.action
            ?: return ToolResult.error("Missing required parameter: action")

        return try {
            val windowId = IdType(args.windowId)
            val window = windowControl.windowRepository.getWindow(windowId)
                ?: return ToolResult.error("Window not found: ${args.windowId}", "WINDOW_NOT_FOUND")

            when (action) {
                Action.CLOSE -> {
                    if (!windowControl.isWindowRemovable(window)) {
                        return ToolResult.error("Cannot close the last window", "CANNOT_CLOSE")
                    }
                    withContext(Dispatchers.Main.immediate) {
                        windowControl.closeWindow(window)
                    }
                    typedSuccess(Result(
                        windowId = args.windowId,
                        action = action.camelCase,
                        newState = null,
                        message = "Window closed"
                    ))
                }
                Action.MINIMIZE -> {
                    if (!windowControl.isWindowMinimizable(window)) {
                        return ToolResult.error(
                            "Cannot minimize: window is already minimized or is the last visible window",
                            "CANNOT_MINIMIZE"
                        )
                    }
                    withContext(Dispatchers.Main.immediate) {
                        windowControl.minimiseWindow(window)
                    }
                    typedSuccess(Result(
                        windowId = args.windowId,
                        action = action.camelCase,
                        newState = window.windowState.toString(),
                        message = "Window minimized"
                    ))
                }
                Action.RESTORE -> {
                    if (!window.isMinimised) {
                        return ToolResult.error("Window is not minimized", "NOT_MINIMIZED")
                    }
                    withContext(Dispatchers.Main.immediate) {
                        windowControl.restoreWindow(window)
                    }
                    typedSuccess(Result(
                        windowId = args.windowId,
                        action = action.camelCase,
                        newState = window.windowState.toString(),
                        message = "Window restored"
                    ))
                }
            }
        } catch (e: Exception) {
            ToolResult.error("Failed to manage window: ${e.message}", "MANAGE_ERROR")
        }
    }
}
