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
            // strictContextMatching=false: focuses on original languages, Bible version doesn't matter
            AgentPrompt(
                name = context.getString(R.string.default_prompt_word_study),
                description = context.getString(R.string.default_prompt_word_study_desc),
                promptTemplate = """
                    Analyze the original Hebrew/Greek words in the selected text.
                    If Strongs numbers are available, use getDictionaryEntry to look up definitions.
                    Include links to dictionary entries: [Strong's G2316](sword://StrongsGreek/G2316)
                    Cite each dictionary source by name when referencing definitions.
                    Explain the etymology, usage, and theological significance of key terms.
                    Use ${getUiLanguageName()} for your response.
                """.trimIndent(),
                showIn = setOf(
                    PromptContext.VERSE_SELECTION,
                    PromptContext.TEXT_SELECTION
                ),
                orderNumber = order++,
                strictContextMatching = false,
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
            // strictContextMatching=false: cross-references are same across all Bible versions
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
                strictContextMatching = false,
            ),

            // TEST: Create bookmark with note (action-only, no document)
            AgentPrompt(
                name = "🧪 Test: Create Bookmark",
                description = "Test bookmark creation without creating a document",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Create a bookmark for the selected verses using createBookmark
                    2. Add a short note explaining the key theme using addBookmarkNote
                    3. Call finishWithoutDocument with a message confirming what you created

                    This is an action-only task - do NOT write a document response.
                    Use finishWithoutDocument to end the task after creating the bookmark.
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

            // TEST: Dictionary lookup
            AgentPrompt(
                name = "🧪 Test: Dictionary",
                description = "Test dictionary entry retrieval",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Use getInstalledDocuments to find available dictionaries
                    2. If a dictionary is available, use getDictionaryEntry to look up a key term from the selected text
                    3. Report what you found

                    If no dictionary is installed, explain that.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            // TEST: Read bookmarks
            AgentPrompt(
                name = "🧪 Test: Read Bookmarks",
                description = "Test reading bookmarks for verses and by label",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Use getBookmarksForVerse to check if the selected verses have any bookmarks
                    2. Use getAllLabels to list available labels
                    3. If labels exist, use getBookmarksWithLabel to get bookmarks for the first label
                    4. Report what you found

                    Summarize any bookmarks and their notes.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            // TEST: Labels management (action-only)
            AgentPrompt(
                name = "🧪 Test: Labels",
                description = "Test label creation and assignment without document",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Use getAllLabels to list existing labels
                    2. Create a new label called "Test Label" using createLabel
                    3. Create a bookmark for the selected verses using createBookmark
                    4. Add the new label to the bookmark using addLabelToBookmark
                    5. Call finishWithoutDocument with a message confirming what you created

                    Note: If "Test Label" already exists, use a different unique name.
                    This is an action-only task - use finishWithoutDocument to end.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            // TEST: StudyPad operations
            AgentPrompt(
                name = "🧪 Test: StudyPad",
                description = "Test StudyPad read/write operations",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Use getAllLabels to find StudyPads (labels can be used as StudyPads)
                    2. Use searchStudyPads to search for any existing content
                    3. If a StudyPad exists, use getStudyPadContent to read its content
                    4. Add a new entry to a StudyPad using addStudyPadEntry with a brief note about the selected verses
                    5. Report what you found and created

                    If no StudyPad exists, create a label first to use as a StudyPad.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            // TEST: Update bookmark note (action-only)
            AgentPrompt(
                name = "🧪 Test: Update Note",
                description = "Test updating bookmark notes without document",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Use getBookmarksForVerse to find bookmarks on the selected verses
                    2. If a bookmark exists with a note, use updateBookmarkNote to append " [Updated by AI]" to the note
                    3. If no bookmark exists, create one first with createBookmark and addBookmarkNote
                    4. Call finishWithoutDocument with a message confirming what you did

                    This is an action-only task - use finishWithoutDocument to end.
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
