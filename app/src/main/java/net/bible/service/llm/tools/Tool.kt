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

import net.bible.service.llm.agent.AgentContext
import org.json.JSONObject

/**
 * Interface for agent tools that can be called by the LLM.
 *
 * Each tool represents a specific action the agent can perform,
 * such as reading verse content, searching, or creating bookmarks.
 */
interface Tool {
    /**
     * Unique name of the tool, used in OpenAI function calling.
     * Should be camelCase, e.g., "getVerseContent", "searchBible".
     */
    val name: String

    /**
     * Description of what this tool does, shown to the LLM.
     * Should be clear and concise to help the LLM decide when to use it.
     */
    val description: String

    /**
     * JSON Schema for the tool's parameters.
     * Used to validate arguments and shown to the LLM.
     *
     * Example:
     * ```json
     * {
     *   "type": "object",
     *   "properties": {
     *     "book": { "type": "string", "description": "Book initials, e.g., KJV" },
     *     "verseRef": { "type": "string", "description": "Verse reference, e.g., Matt.5.3" }
     *   },
     *   "required": ["book", "verseRef"]
     * }
     * ```
     */
    val parametersSchema: JSONObject

    /**
     * Whether this tool requires user permission before execution.
     * Read-only tools typically don't require permission (false).
     * Write tools (creating bookmarks, documents) should require permission (true).
     */
    val requiresPermission: Boolean
        get() = false

    /**
     * String resource ID for the user-facing display name of this tool.
     * Used in permission dialogs and settings UI.
     * Return 0 to use the tool's code name as fallback.
     */
    val displayNameResId: Int
        get() = 0

    /**
     * Execute the tool with the given arguments.
     *
     * @param arguments JSON object containing the tool arguments, matching parametersSchema
     * @param context Agent execution context with information about the current state
     * @return Result of the tool execution
     */
    suspend fun execute(arguments: JSONObject, context: AgentContext): ToolResult
}

/**
 * Normalize text content from LLM output.
 *
 * Some LLM providers return literal "\n" (backslash + n) in their JSON tool call
 * arguments instead of actual newline characters. This happens due to double-escaping
 * in the JSON arguments string. This function converts those literal escape sequences
 * back to actual characters.
 *
 * Also strips tool-call artifacts that some LLMs embed in their text output
 * (e.g. `<function_call>` tags, `setDocumentTitle(...)` syntax).
 */
/**
 * Strip markdown formatting from a title string.
 * Converts `[text](url)` links to just `text`, removes bold/italic markers, etc.
 * LLMs sometimes put markdown links in titles despite being told not to.
 */
private val markdownLinkRegex = Regex("""\[([^\]]*)\]\([^)]*\)""")
fun stripMarkdownFromTitle(title: String): String = title
    .replace(markdownLinkRegex, "$1")
    .replace("**", "")
    .replace("__", "")
    .replace("*", "")
    .replace("_", "")
    .replace("`", "")
    .replace("#", "")
    .trim()

fun normalizeLlmText(text: String): String = text
    .replace("\\n", "\n")
    .replace("\\t", "\t")
    .let { stripToolCallArtifacts(it) }

/**
 * Patterns for tool-call artifacts that LLMs sometimes embed in text content.
 *
 * Some providers (especially smaller models or those with limited tool-calling support)
 * output tool calls as text rather than structured JSON. This results in tags like
 * `<function_call name="setDocumentTitle">...</function_call>` appearing in the
 * document content.
 */
private val functionCallTagRegex = Regex(
    """<function_call\b[^>]*>.*?</function_call>""",
    setOf(RegexOption.DOT_MATCHES_ALL)
)
private val toolCallTagRegex = Regex(
    """<tool_call\b[^>]*>.*?</tool_call>""",
    setOf(RegexOption.DOT_MATCHES_ALL)
)
private val trailingToolCallRegex = Regex(
    """\n*(?:finishWith(?:Document|outDocument|StudyPad)|setDocumentTitle)\s*\(.*$""",
    setOf(RegexOption.DOT_MATCHES_ALL)
)

/**
 * Strip tool-call artifacts from LLM text output.
 */
fun stripToolCallArtifacts(text: String): String = text
    .replace(functionCallTagRegex, "")
    .replace(toolCallTagRegex, "")
    .replace(trailingToolCallRegex, "")
    .trim()

