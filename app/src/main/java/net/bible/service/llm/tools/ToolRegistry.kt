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
import net.bible.service.llm.ToolCategory
import net.bible.service.llm.tools.read.GetAllLabelsTool
import net.bible.service.llm.tools.read.GetBookmarksForVerseTool
import net.bible.service.llm.tools.read.GetBookmarksWithLabelTool
import net.bible.service.llm.tools.read.GetCommentariesTool
import net.bible.service.llm.tools.read.GetDictionaryEntryTool
import net.bible.service.llm.tools.read.GetInstalledDocumentsTool
import net.bible.service.llm.tools.read.GetGenBookContentTool
import net.bible.service.llm.tools.read.GetGenBookKeysTool
import net.bible.service.llm.tools.read.GetMyDocumentPagesTool
import net.bible.service.llm.tools.read.GetMyDocumentsTool
import net.bible.service.llm.tools.read.GetStudyPadContentTool
import net.bible.service.llm.tools.read.GetVerseContentTool
import net.bible.service.llm.tools.read.SearchBibleTool
import net.bible.service.llm.tools.read.SearchByStrongsNumberTool
import net.bible.service.llm.tools.read.GetWindowsTool
import net.bible.service.llm.tools.read.SearchStudyPadsTool
import net.bible.service.llm.tools.write.AddBookmarkNoteTool
import net.bible.service.llm.tools.write.AddLabelToBookmarkTool
import net.bible.service.llm.tools.write.AddMyDocumentPageTool
import net.bible.service.llm.tools.write.AddStudyPadEntryTool
import net.bible.service.llm.tools.write.CreateStudyPadTool
import net.bible.service.llm.tools.write.CreateBookmarkTool
import net.bible.service.llm.tools.write.CreateLabelTool
import net.bible.service.llm.tools.write.CreateMyDocumentTool
import net.bible.service.llm.tools.write.DeleteBookmarkTool
import net.bible.service.llm.tools.write.DeleteLabelTool
import net.bible.service.llm.tools.write.DeleteMyDocumentPageTool
import net.bible.service.llm.tools.write.EditMyDocumentPageTool
import net.bible.service.llm.tools.write.SetDocumentTitleTool
import net.bible.service.llm.tools.write.FinishWithMyDocumentPageTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.CreateWindowTool
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import net.bible.service.llm.tools.write.ManageWindowTool
import net.bible.service.llm.tools.write.RemoveLabelFromBookmarkTool
import net.bible.service.llm.tools.write.SetWindowDocumentTool
import net.bible.service.llm.tools.write.UpdateBookmarkNoteTool
import net.bible.service.llm.tools.write.UpdateStudyPadTextEntryTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        register(SearchByStrongsNumberTool)
        register(GetCommentariesTool)
        register(GetDictionaryEntryTool)
        register(GetBookmarksForVerseTool)
        register(GetBookmarksWithLabelTool)
        register(GetAllLabelsTool)
        register(GetStudyPadContentTool)
        register(SearchStudyPadsTool)
        register(GetInstalledDocumentsTool)
        register(GetMyDocumentsTool)
        register(GetMyDocumentPagesTool)
        register(GetGenBookKeysTool)
        register(GetGenBookContentTool)
        register(GetWindowsTool)

        // Register write tools
        register(CreateBookmarkTool)
        register(AddBookmarkNoteTool)
        register(UpdateBookmarkNoteTool)
        register(CreateLabelTool)
        register(AddLabelToBookmarkTool)
        register(DeleteBookmarkTool)
        register(DeleteLabelTool)
        register(RemoveLabelFromBookmarkTool)
        register(AddStudyPadEntryTool)
        register(UpdateStudyPadTextEntryTool)
        register(CreateStudyPadTool)
        register(CreateMyDocumentTool)
        register(AddMyDocumentPageTool)
        register(EditMyDocumentPageTool)
        register(DeleteMyDocumentPageTool)
        register(CreateWindowTool)
        register(ManageWindowTool)
        register(SetWindowDocumentTool)
        register(SetDocumentTitleTool)
        register(FinishWithStudyPadTool)
        register(FinishWithMyDocumentPageTool)
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

    /** Tools that control agent flow and must never be excluded from tool definitions. */
    val STRUCTURAL_TOOLS: Set<AgentTool> = setOf(
        AgentTool.SET_DOCUMENT_TITLE,
        AgentTool.FINISH_WITH_STUDY_PAD,
        AgentTool.FINISH_WITH_MY_DOCUMENT_PAGE,
        AgentTool.FINISH_WITHOUT_DOCUMENT
    )

    /**
     * Get provider-neutral tool definitions for use with [LlmApiAdapter.buildToolsArray].
     *
     * Non-structural tools get `taskComplete` and `taskCompleteMessage` optional parameters
     * injected into their schema, allowing the LLM to signal task completion on any tool call.
     *
     * @param excludedTools Tools to omit from the definitions (saves context tokens).
     *   Structural tools ([STRUCTURAL_TOOLS]) are never excluded regardless of this set.
     */
    fun getToolDefinitions(excludedTools: Set<AgentTool> = emptySet()): List<ToolDefinition> {
        return tools.values
            .filter { it.agentTool !in excludedTools || it.agentTool in STRUCTURAL_TOOLS }
            .map { tool ->
                val schema = if (tool.agentTool in STRUCTURAL_TOOLS) {
                    tool.parametersSchema
                } else {
                    injectTaskCompleteProperties(tool.parametersSchema)
                }
                ToolDefinition(tool.agentTool, tool.description, schema)
            }
    }

    /**
     * Inject `taskComplete` and `taskCompleteMessage` optional properties into a tool's
     * parameter schema. These allow the LLM to signal task completion alongside any tool call,
     * eliminating the need for a separate `finishWithoutDocument` call.
     */
    private fun injectTaskCompleteProperties(schema: JsonObject): JsonObject {
        val properties = schema["properties"] as? JsonObject ?: return schema
        val augmented = JsonObject(properties + mapOf(
            "taskComplete" to JsonObject(mapOf(
                "type" to JsonPrimitive("boolean"),
                "description" to JsonPrimitive(
                    "Set to true if this tool call completes the entire task and no further actions or document output are needed."
                )
            )),
            "taskCompleteMessage" to JsonObject(mapOf(
                "type" to JsonPrimitive("string"),
                "description" to JsonPrimitive(
                    "Brief message confirming what was done (shown to user). Required when taskComplete is true."
                )
            ))
        ))
        return JsonObject(schema + mapOf("properties" to augmented))
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
     * Get all tools that can be configured in per-prompt permissions.
     * Excludes structural tools. Sorted: read tools first, then write tools, alphabetically.
     */
    fun getConfigurableTools(): List<Tool> =
        tools.values
            .filter { it.agentTool !in STRUCTURAL_TOOLS }
            .sortedWith(compareBy({ it.requiresPermission }, { getDisplayName(it) }))

    /**
     * Get all tools sorted by category (read first, then write) and display name within each category.
     */
    fun getAllTools(): List<Tool> =
        tools.values.sortedWith(compareBy({ it.requiresPermission }, { getDisplayName(it) }))

    /**
     * Get configurable tools grouped by [ToolCategory], ordered by category ordinal.
     * Within each category, read tools come first, then write tools, alphabetically.
     */
    fun getConfigurableToolsByCategory(): Map<ToolCategory, List<Tool>> =
        getConfigurableTools()
            .groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })

    /** Get the localized display name for a [ToolCategory]. */
    fun getCategoryDisplayName(category: ToolCategory): String =
        BibleApplication.application.getString(category.displayNameResId)

    /**
     * Clear all registered tools (mainly for testing).
     */
    fun clear() {
        tools.clear()
        Log.i(TAG, "Cleared all tools")
    }
}
