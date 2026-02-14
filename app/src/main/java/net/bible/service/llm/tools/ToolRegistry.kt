/*
 * Copyright (c) 2024 Martin Denham, Tuomas Airaksinen and the AndBible contributors.
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
import net.bible.service.llm.tools.write.FinishWithDocumentTool
import net.bible.service.llm.tools.write.FinishWithStudyPadTool
import net.bible.service.llm.tools.write.FinishWithoutDocumentTool
import net.bible.service.llm.tools.write.UpdateBookmarkNoteTool
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "ToolRegistry"

/**
 * Registry for agent tools.
 *
 * Manages registration of tools and provides them to the agent executor.
 * Tools are registered at application startup and remain available throughout.
 */
object ToolRegistry {
    private val tools = ConcurrentHashMap<String, Tool>()

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
        register(FinishWithDocumentTool)
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
        val existing = tools.putIfAbsent(tool.name, tool)
        if (existing != null) {
            throw IllegalArgumentException("Tool '${tool.name}' is already registered")
        }
        Log.i(TAG, "Registered tool: ${tool.name}")
    }

    /**
     * Get a tool by name.
     *
     * @param name The tool name
     * @return The tool, or null if not found
     */
    fun get(name: String): Tool? = tools[name]

    /**
     * Check if a tool is registered.
     *
     * @param name The tool name
     * @return true if the tool is registered
     */
    fun has(name: String): Boolean = tools.containsKey(name)

    /**
     * Get the count of registered tools.
     */
    val count: Int get() = tools.size

    /**
     * Generate the tools array for OpenAI API request.
     *
     * Returns a JSONArray in the format expected by the OpenAI API:
     * ```json
     * [
     *   {
     *     "type": "function",
     *     "function": {
     *       "name": "toolName",
     *       "description": "Tool description",
     *       "parameters": { ... }
     *     }
     *   }
     * ]
     * ```
     *
     * @param includeWriteTools Whether to include write tools that require permission
     */
    fun toOpenAiToolsArray(includeWriteTools: Boolean = true): JSONArray {
        val toolsArray = JSONArray()
        for (tool in tools.values) {
            if (!includeWriteTools && tool.requiresPermission) {
                continue
            }
            val toolObj = JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("parameters", tool.parametersSchema)
                })
            }
            toolsArray.put(toolObj)
        }
        return toolsArray
    }

    /**
     * Clear all registered tools (mainly for testing).
     */
    fun clear() {
        tools.clear()
        Log.i(TAG, "Cleared all tools")
    }
}
