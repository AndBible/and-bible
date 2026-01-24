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
                name = context.getString(R.string.default_prompt_translate_to_language, getUiLanguageName()),
                description = context.getString(R.string.default_prompt_translate_to_ui_language_desc),
                promptTemplate = """
                    Translate the following text to ${getUiLanguageName()}.

                    You MAY use getInstalledDocuments to check if a ${getUiLanguageName()} Bible translation is installed.
                    If one exists, use getVerseContent to get the text from that translation.
                    If NO ${getUiLanguageName()} translation is installed, translate the text yourself directly.

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

            // Translate to English - for multiple contexts
            AgentPrompt(
                name = context.getString(R.string.default_prompt_translate_to_english),
                description = context.getString(R.string.default_prompt_translate_to_english_desc),
                promptTemplate = """
                    Translate the following text to English.

                    You MAY use getInstalledDocuments to check if an English Bible translation is installed.
                    If one exists, use getVerseContent to get the text from that translation.
                    If NO English translation is installed, translate the text yourself directly.

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
        ) + createTestPrompts(order)
    }

    /**
     * Creates test prompts for development/testing purposes.
     * These can be removed or disabled after testing phase.
     */
    private fun createTestPrompts(startOrder: Int): List<AgentPrompt> {
        var order = startOrder

        return listOf(
            // TEST: Tool calling - read verses from different documents
            AgentPrompt(
                name = "🧪 Test: Tool Calling",
                description = "Test that tools work correctly",
                promptTemplate = """
                    This is a test prompt. Please do the following:
                    1. Use getInstalledDocuments to list available Bible translations
                    2. Pick one Bible translation and use getVerseContent to read John 3:16 from it
                    3. Report what you found

                    Format your response with clear headings for each step.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
            ),

            // TEST: Cross-references with sword:// links
            AgentPrompt(
                name = "🧪 Test: Cross-References",
                description = "Test that cross-reference links are formatted correctly",
                promptTemplate = """
                    This is a test prompt. Analyze the selected verses and:
                    1. Find 3-5 related Bible passages
                    2. Format each as a clickable link using sword:// protocol
                    3. Briefly explain why each passage is related

                    Remember to use the format: [Display Text](sword:///Book.Chapter.Verse)
                    Example: [John 3:16](sword:///John.3.16)
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            // TEST: Create bookmark with note
            AgentPrompt(
                name = "🧪 Test: Create Bookmark",
                description = "Test bookmark creation with notes",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Create a bookmark for the selected verses
                    2. Add a short note explaining the key theme of these verses
                    3. Report what you created

                    Use the createBookmark and addBookmarkNote tools.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            // TEST: Search and summarize
            AgentPrompt(
                name = "🧪 Test: Search Bible",
                description = "Test Bible search functionality",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Use searchBible to find verses containing the word "love"
                    2. Summarize the top 5 results
                    3. Create cross-reference links to each result

                    Report your findings with proper sword:// links.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
            ),

            // TEST: Commentary lookup
            AgentPrompt(
                name = "🧪 Test: Commentary",
                description = "Test commentary retrieval",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Use getInstalledDocuments to find available commentaries
                    2. If a commentary is available, use getCommentaries to get commentary on the selected verses
                    3. Summarize what the commentary says

                    If no commentary is installed, explain that and provide your own brief commentary.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
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

    /**
     * Reset prompts to defaults by deleting all existing prompts
     * and recreating the default set.
     */
    fun resetToDefaults() {
        val dao = DatabaseContainer.instance.llmProcessingDb.agentPromptDao()
        // Delete all existing prompts
        dao.allPrompts().forEach { dao.delete(it) }
        // Recreate defaults
        createDefaultPrompts().forEach { dao.insert(it) }
    }
}
