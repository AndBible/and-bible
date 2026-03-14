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

import android.util.Log
import net.bible.android.BibleApplication
import net.bible.service.llm.AgentTool
import net.bible.service.llm.tools.read.GetAllLabelsTool
import net.bible.service.llm.tools.read.GetBookmarksForVerseTool
import net.bible.service.llm.tools.read.GetBookmarksWithLabelTool
import net.bible.service.llm.tools.read.GetCommentariesTool
import net.bible.service.llm.tools.read.GetDictionaryEntryTool
import net.bible.service.llm.tools.read.GetInstalledDocumentsTool
import net.bible.service.llm.tools.read.GetStudyPadContentTool
import net.bible.service.llm.tools.read.GetVerseContentTool
import net.bible.service.llm.tools.read.SearchBibleTool
import net.bible.service.llm.tools.read.SearchStudyPadsTool
import net.bible.service.llm.tools.write.AddBookmarkNoteTool
import net.bible.service.llm.tools.write.AddLabelToBookmarkTool
import net.bible.service.llm.tools.write.AddStudyPadEntryTool
import net.bible.service.llm.tools.write.CreateBookmarkTool
import net.bible.service.llm.tools.write.CreateLabelTool
import net.bible.service.llm.tools.write.SetDocumentTitleTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import net.bible.service.llm.tools.write.UpdateBookmarkNoteTool
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ToolRegistry"

/**
 * Provider-neutral tool definition (name, description, parameters schema).
 * Used by [LlmApiAdapter] implementations to build provider-specific tool arrays.
 */
data class ToolDefinition(
    val tool: AgentTool,
    val description: String,
    val parametersSchema: JsonObject
) {
    /** camelCase name for the wire format (JSON tool definitions sent to LLM). */
    val name: String get() = tool.camelCaseName
}

/**
 * Registry for agent tools.
 *
 * Manages registration of tools and provides them to the agent executor.
 * Tools are registered at application startup and remain available throughout.
 */
object ToolRegistry {
    private val tools = ConcurrentHashMap<AgentTool, Tool>()

    init {
        // Register read tools
        register(GetVerseContentTool)
        register(SearchBibleTool)
        register(GetCommentariesTool)
        register(GetDictionaryEntryTool)
        register(GetBookmarksForVerseTool)
        register(GetBookmarksWithLabelTool)
        register(GetAllLabelsTool)
        register(GetStudyPadContentTool)
        register(SearchStudyPadsTool)
        register(GetInstalledDocumentsTool)

        // Register write tools
        register(CreateBookmarkTool)
        register(AddBookmarkNoteTool)
        register(UpdateBookmarkNoteTool)
        register(CreateLabelTool)
        register(AddLabelToBookmarkTool)
        register(AddStudyPadEntryTool)
        register(SetDocumentTitleTool)
        register(FinishWithStudyPadTool)
        register(FinishWithoutDocumentTool)

        Log.i(TAG, "ToolRegistry initialized with ${tools.size} tools")
    }

    /**
     * Register a tool for use by the agent.
     *
     * @param tool The tool to register
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    fun register(tool: Tool) {
        val existing = tools.putIfAbsent(tool.agentTool, tool)
        if (existing != null) {
            throw IllegalArgumentException("Tool '${tool.agentTool.camelCaseName}' is already registered")
        }
        Log.i(TAG, "Registered tool: ${tool.agentTool.camelCaseName}")
    }

    /** Get a tool by its AgentTool enum value. */
    fun get(tool: AgentTool): Tool? = tools[tool]

    /** Get a tool by its camelCase name (for backward compatibility). */
    fun get(name: String): Tool? {
        val agentTool = AgentTool.fromToolName(name) ?: return null
        return tools[agentTool]
    }

    /** Check if a tool is registered by its camelCase name. */
    fun has(name: String): Boolean {
        val agentTool = AgentTool.fromToolName(name) ?: return false
        return tools.containsKey(agentTool)
    }

    /**
     * Get the count of registered tools.
     */
    val count: Int get() = tools.size

    /**
     * Get provider-neutral tool definitions for use with [LlmApiAdapter.buildToolsArray].
     *
     * @param includeWriteTools Whether to include write tools that require permission
     */
    fun getToolDefinitions(includeWriteTools: Boolean = true): List<ToolDefinition> {
        return tools.values
            .filter { includeWriteTools || !it.requiresPermission }
            .map { ToolDefinition(it.agentTool, it.description, it.parametersSchema) }
    }

    /**
     * Get the user-facing display name for a tool.
     * Uses the translated string resource if available, otherwise falls back to the code name.
     */
    fun getDisplayName(tool: Tool): String =
        if (tool.displayNameResId != 0) BibleApplication.application.getString(tool.displayNameResId)
        else tool.agentTool.camelCaseName

    /**
     * Get all tools that require user permission (write tools), sorted by display name.
     */
    fun getPermissionTools(): List<Tool> =
        tools.values.filter { it.requiresPermission }.sortedBy { getDisplayName(it) }

    /**
     * Get all tools sorted by category (read first, then write) and display name within each category.
     */
    fun getAllTools(): List<Tool> =
        tools.values.sortedWith(compareBy({ it.requiresPermission }, { getDisplayName(it) }))

    /**
     * Clear all registered tools (mainly for testing).
     */
    fun clear() {
        tools.clear()
        Log.i(TAG, "Cleared all tools")
    }
}
