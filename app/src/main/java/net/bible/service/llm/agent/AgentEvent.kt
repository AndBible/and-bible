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
import net.bible.service.llm.AgentTool
import net.bible.service.llm.LlmUsage
import net.bible.service.llm.tools.ToolResult

/** Events emitted during agent execution for UI progress tracking. */
sealed class AgentEvent {
    data object Started : AgentEvent()
    data class Iteration(val number: Int) : AgentEvent()

    data class ToolCalling(
        val toolCallId: String,
        val tool: AgentTool,
        val arguments: String
    ) : AgentEvent()

    data class ToolCompleted(
        val toolCallId: String,
        val tool: AgentTool,
        val result: ToolResult
    ) : AgentEvent()

    data class ApiCallCompleted(val usage: LlmUsage, val model: String) : AgentEvent()

    data class TextResponse(val text: String, val isFinal: Boolean) : AgentEvent()

    data class Completed(
        val response: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    /** Agent called finishWithoutDocument — task done, no AI document created. */
    data class CompletedWithoutDocument(
        val message: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    /** Agent called setDocumentTitle — title from tool, content from text response. */
    data class CompletedWithDocument(
        val title: String,
        val content: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    /** Agent called finishWithStudyPad — opens an existing StudyPad. */
    data class CompletedWithStudyPad(
        val labelId: IdType,
        val scrollToEntryId: IdType?,
        val message: String,
        val totalIterations: Int,
        val usage: LlmUsage = LlmUsage(),
        val model: String = ""
    ) : AgentEvent()

    data class Error(val message: String, val cause: Throwable? = null) : AgentEvent()
    data object Cancelled : AgentEvent()
}
