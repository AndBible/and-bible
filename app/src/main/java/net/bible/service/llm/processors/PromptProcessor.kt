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

package net.bible.service.llm.processors

import net.bible.android.database.IdType
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmProcessor

/**
 * LLM Processor that uses AgentPrompt from the database.
 *
 * The processingParams is the prompt ID (as string).
 * The system prompt is loaded from the AgentPrompt's promptTemplate field.
 */
object PromptProcessor : LlmProcessor {
    override val processorId: String = "prompt"

    override fun getSystemPrompt(processingParams: String): String {
        val promptId = IdType.fromString(processingParams)
        val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
        val prompt = dao.promptById(promptId)

        return if (prompt != null) {
            // Build a complete system prompt including context and the user's template
            """
You are an AI assistant for AndBible, a Bible study application.

${prompt.promptTemplate}

Important instructions:
- Preserve XML structure exactly, only modify text content between tags
- Do not add explanations, commentary, or markdown formatting
- Return only the processed XML content
""".trimIndent()
        } else {
            // Fallback if prompt not found
            """
You are an AI assistant for AndBible, a Bible study application.
Process the following content. Preserve XML structure exactly.
""".trimIndent()
        }
    }

    override fun getDescription(processingParams: String): String {
        val promptId = IdType.fromString(processingParams)
        val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
        val prompt = dao.promptById(promptId)
        return prompt?.name ?: "AI Processing"
    }
}
