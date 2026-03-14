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

package net.bible.service.llm.agent

import net.bible.android.database.IdType
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.tools.ToolResult

/**
 * Events emitted during agent execution.
 *
 * These events can be observed to show progress in the UI (Agent Log),
 * update status indicators, etc.
 */
sealed class AgentEvent {
    /**
     * Agent execution has started.
     */
    data object Started : AgentEvent()

    /**
     * Starting a new iteration of the agent loop.
     *
     * @param number The iteration number (1-based)
     */
    data class Iteration(val number: Int) : AgentEvent()

    /**
     * LLM is calling a tool.
     *
     * @param toolCallId The unique ID of this tool call (from LLM response)
     * @param toolName Name of the tool being called
     * @param arguments JSON string of arguments
     */
    data class ToolCalling(
        val toolCallId: String,
        val toolName: String,
        val arguments: String
    ) : AgentEvent()

    /**
     * Tool execution completed.
     *
     * @param toolCallId The unique ID of this tool call
     * @param toolName Name of the tool that was called
     * @param result The result of the tool execution
     */
    data class ToolCompleted(
        val toolCallId: String,
        val toolName: String,
        val result: ToolResult
    ) : AgentEvent()

    /**
     * An API call completed with token usage information.
     *
     * @param usage Token usage from this API call
     * @param model The model used for this API call
     */
    data class ApiCallCompleted(val usage: LlmUsage, val model: String) : AgentEvent()

    /**
     * LLM returned a text response (potentially intermediate).
     *
     * @param text The text content from the LLM
     * @param isFinal Whether this is the final response (no more tool calls expected)
     */
    data class TextResponse(
        val text: String,
        val isFinal: Boolean
    ) : AgentEvent()

    /**
     * Agent execution completed successfully.
     *
     * @param response The final response text from the LLM
     * @param totalIterations Total number of iterations taken
     * @param usage Cumulative token usage for this session
     */
    data class Completed(
        val response: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    /**
     * Agent execution completed without creating a document.
     *
     * Used when the agent called finishWithoutDocument tool to indicate
     * that the task is done but no AI document should be created.
     *
     * @param message A brief message about what was done
     * @param totalIterations Total number of iterations taken
     * @param usage Cumulative token usage for this session
     */
    data class CompletedWithoutDocument(
        val message: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    /**
     * Agent execution completed with a document to save.
     *
     * Used when the agent called setDocumentTitle tool to explicitly
     * provide the title, with content from the text response.
     *
     * @param title Plain text title for the document (used in TOC)
     * @param content Full markdown content including title with links
     * @param totalIterations Total number of iterations taken
     * @param usage Cumulative token usage for this session
     */
    data class CompletedWithDocument(
        val title: String,
        val content: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    /**
     * Agent execution completed by opening a StudyPad.
     *
     * Used when the agent called finishWithStudyPad tool to open an
     * already-created StudyPad as the result.
     *
     * @param labelId ID of the StudyPad (label) to open
     * @param scrollToEntryId Optional ID of an entry to scroll to
     * @param message Brief message about what was done
     * @param totalIterations Total number of iterations taken
     * @param usage Cumulative token usage for this session
     */
    data class CompletedWithStudyPad(
        val labelId: IdType,
        val scrollToEntryId: IdType?,
        val message: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    /**
     * Agent execution failed.
     *
     * @param message Error description
     * @param cause Optional underlying exception
     */
    data class Error(
        val message: String,
        val cause: Throwable? = null
    ) : AgentEvent()

    /**
     * Agent execution was cancelled.
     */
    data object Cancelled : AgentEvent()
}
