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

package net.bible.service.llm

/**
 * Interface for LLM content processors.
 *
 * Each processor defines how to transform document content using an LLM.
 * Examples: translation, summarization, analysis, custom prompts.
 */
interface LlmProcessor {
    /**
     * Unique identifier for this processor type.
     * Used in book initials format: {original}/{processorId}/{params}
     * Examples: "translations", "summaries", "user"
     */
    val processorId: String

    /**
     * Get the system prompt for the LLM based on processing parameters.
     *
     * @param params Processing-specific parameters (e.g., target language for translation)
     * @return The system prompt to send to the LLM
     */
    fun getSystemPrompt(params: String): String

    /**
     * Generate a unique cache key for this processing operation.
     * Default implementation uses processorId and params.
     *
     * @param documentInitials The original document initials (e.g., "KJV")
     * @param keyName The document key (e.g., "Gen.1")
     * @param params Processing-specific parameters
     * @return A unique cache key
     */
    fun getCacheKey(documentInitials: String, keyName: String, params: String, modelId: String): CacheKey =
        CacheKey(documentInitials, keyName, processorId, params, modelId)

    /**
     * Human-readable description of what this processor does.
     * Used in UI to describe the processing.
     */
    fun getDescription(params: String): String

    /**
     * Get the language code for the processed output.
     * Used for book metadata (Lang=) and TTS locale selection.
     *
     * @param params Processing-specific parameters
     * @return BCP 47 language code (e.g., "fi", "en", "de")
     */
    fun getLanguageCode(params: String): String
}

/**
 * Cache key for LLM processing results.
 */
data class CacheKey(
    val documentInitials: String,
    val keyName: String,
    val processingType: String,
    val processingParams: String,
    val modelId: String
)
