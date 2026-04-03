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

/** Shared fields for all completion events (for cost tracking and logging). */
interface CompletionEvent {
    val totalIterations: Int
    val usage: LlmUsage
    val model: String
    val configuredModelId: IdType?
}

/** Events emitted during agent execution for UI progress tracking. */
sealed class AgentEvent {
    data class Started(val model: String) : AgentEvent()
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

    data class ApiCallCompleted(val usage: LlmUsage, val model: String, val configuredModelId: IdType? = null) : AgentEvent()

    data class TextResponse(val text: String, val isFinal: Boolean) : AgentEvent()

    data class Completed(
        val response: String,
        override val totalIterations: Int,
        override val usage: LlmUsage = LlmUsage(),
        override val model: String = "",
        override val configuredModelId: IdType? = null
    ) : AgentEvent(), CompletionEvent

    /** Agent called finishWithoutDocument — task done, no AI document created. */
    data class CompletedWithoutDocument(
        val message: String,
        override val totalIterations: Int,
        override val usage: LlmUsage = LlmUsage(),
        override val model: String = "",
        override val configuredModelId: IdType? = null
    ) : AgentEvent(), CompletionEvent

    /** Agent called setDocumentTitle — title from tool, content from text response. */
    data class CompletedWithDocument(
        val title: String,
        val content: String,
        override val totalIterations: Int,
        override val usage: LlmUsage = LlmUsage(),
        override val model: String = "",
        override val configuredModelId: IdType? = null
    ) : AgentEvent(), CompletionEvent

    /** Agent called finishWithStudyPad — opens an existing StudyPad. */
    data class CompletedWithStudyPad(
        val labelId: IdType,
        val scrollToEntryId: IdType?,
        val message: String,
        override val totalIterations: Int,
        override val usage: LlmUsage = LlmUsage(),
        override val model: String = "",
        override val configuredModelId: IdType? = null
    ) : AgentEvent(), CompletionEvent

    /** Agent called finishWithMyDocumentPage — opens an existing My Documents page. */
    data class CompletedWithMyDocumentPage(
        val documentInitials: String,
        val pageKey: String,
        val message: String,
        override val totalIterations: Int,
        override val usage: LlmUsage = LlmUsage(),
        override val model: String = "",
        override val configuredModelId: IdType? = null
    ) : AgentEvent(), CompletionEvent

    data class Error(val message: String, val cause: Throwable? = null) : AgentEvent()
    data object Cancelled : AgentEvent()
}
