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

import android.util.Log
import net.bible.android.database.IdType
import net.bible.service.llm.LlmProcessor
import net.bible.service.llm.PromptRepository
import java.util.Locale

private const val TAG = "PromptProcessor"

/**
 * LLM Processor that uses AgentPrompt from built-in prompts or the database.
 *
 * The processingParams is the prompt ID (as string).
 * The system prompt is loaded from the AgentPrompt's promptTemplate field.
 */
object PromptProcessor : LlmProcessor {
    override val processorId: String = "prompt"

    override fun getSystemPrompt(processingParams: String): String {
        val promptId = IdType.fromString(processingParams)
        val prompt = PromptRepository.promptById(promptId)

        Log.d(TAG, "getSystemPrompt: promptId=$promptId, found=${prompt != null}")
        Log.d(TAG, "getSystemPrompt: promptTemplate=${prompt?.promptTemplate?.take(200)}")

        // Get UI language for context
        val uiLanguage = Locale.getDefault().displayLanguage

        return if (prompt != null) {
            // Build a complete system prompt including context and the user's template
            """
You are an AI assistant for AndBible, a Bible study application.
You have access to tools for reading Bible content (getVerseContent, getInstalledDocuments, etc.).
Use them when the task requires data from other documents.

Current context:
- UI language: $uiLanguage

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

Current context:
- UI language: $uiLanguage

Process the following content. Preserve XML structure exactly.
""".trimIndent()
        }
    }

    override fun getDescription(processingParams: String): String {
        val promptId = IdType.fromString(processingParams)
        val prompt = PromptRepository.promptById(promptId)
        return prompt?.name ?: "AI Processing"
    }
}
