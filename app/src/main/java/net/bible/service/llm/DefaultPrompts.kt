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

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.service.db.DatabaseContainer
import java.util.Locale

/**
 * Default prompts that are created when LLM is configured for the first time.
 */
object DefaultPrompts {

    /**
     * Creates the default set of prompts.
     * Prompt names and descriptions are localized to the UI language.
     */
    fun createDefaultPrompts(): List<AgentPrompt> {
        val context = BibleApplication.application
        var order = 0

        return listOf(
            // Translate to UI language - for TEXT_DISPLAY_SETTINGS
            AgentPrompt(
                name = context.getString(R.string.default_prompt_translate_to_ui_language),
                description = context.getString(R.string.default_prompt_translate_to_ui_language_desc),
                promptTemplate = """
                    Translate the following text to ${getUiLanguageName()}.
                    Preserve the XML structure exactly, only translate the text content between tags.
                    Do not add any explanations or commentary.
                """.trimIndent(),
                showIn = setOf(PromptContext.TEXT_DISPLAY_SETTINGS),
                orderNumber = order++,
            ),

            // Translate to English - for multiple contexts
            AgentPrompt(
                name = context.getString(R.string.default_prompt_translate_to_english),
                description = context.getString(R.string.default_prompt_translate_to_english_desc),
                promptTemplate = """
                    Translate the following text to English.
                    Preserve the XML structure exactly, only translate the text content between tags.
                    Do not add any explanations or commentary.
                """.trimIndent(),
                showIn = setOf(
                    PromptContext.TEXT_DISPLAY_SETTINGS,
                    PromptContext.VERSE_SELECTION,
                    PromptContext.WINDOW_MENU
                ),
                orderNumber = order++,
            ),

            // Summary - for verse selection and window menu
            AgentPrompt(
                name = context.getString(R.string.default_prompt_summary),
                description = context.getString(R.string.default_prompt_summary_desc),
                promptTemplate = """
                    Create a concise summary of the selected text.
                    Focus on the main theological themes and key points.
                    Use ${getUiLanguageName()} for your response.
                """.trimIndent(),
                showIn = setOf(
                    PromptContext.VERSE_SELECTION,
                    PromptContext.WINDOW_MENU
                ),
                orderNumber = order++,
            ),

            // Explain verses - for verse selection
            AgentPrompt(
                name = context.getString(R.string.default_prompt_explain_verses),
                description = context.getString(R.string.default_prompt_explain_verses_desc),
                promptTemplate = """
                    Explain the meaning and context of the selected verses.
                    Include historical context, theological significance, and practical application.
                    Use ${getUiLanguageName()} for your response.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            // Word study - for verse and text selection
            AgentPrompt(
                name = context.getString(R.string.default_prompt_word_study),
                description = context.getString(R.string.default_prompt_word_study_desc),
                promptTemplate = """
                    Analyze the original Hebrew/Greek words in the selected text.
                    If Strongs numbers are available, use them to identify the original words.
                    Explain the etymology, usage, and theological significance of key terms.
                    Use ${getUiLanguageName()} for your response.
                """.trimIndent(),
                showIn = setOf(
                    PromptContext.VERSE_SELECTION,
                    PromptContext.TEXT_SELECTION
                ),
                orderNumber = order++,
            ),
        )
    }

    private fun getUiLanguageName(): String {
        val locale = Locale.getDefault()
        return locale.getDisplayLanguage(locale)
    }

    private var initialized = false

    /**
     * Initialize default prompts if the database is empty.
     * Should be called when LLM is configured for the first time.
     * Thread-safe: uses synchronized block to prevent double initialization.
     */
    @Synchronized
    fun initializeIfNeeded() {
        if (initialized) return
        val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
        if (dao.getCount() == 0) {
            createDefaultPrompts().forEach { dao.insert(it) }
        }
        initialized = true
    }
}
