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

package net.bible.service.llm

import net.bible.android.BibleApplication
import net.bible.android.activity.R
import net.bible.android.database.IdType
import java.util.Locale
import java.util.UUID

/**
 * Built-in prompts that are hardcoded and read-only.
 *
 * These prompts live in code, not in the database. They have stable deterministic IDs
 * generated from a key string, so they remain consistent across app restarts and updates.
 *
 * Users can copy a built-in prompt to create their own editable version.
 */
object BuiltInPrompts {

    /**
     * Generates a stable deterministic IdType from a key string.
     * Uses UUID v3 (name-based with MD5) for consistency.
     */
    private fun stableId(key: String): IdType {
        val uuid = UUID.nameUUIDFromBytes("andbible-builtin:$key".toByteArray())
        return IdType.fromString(uuid.toString())
    }

    // Stable IDs for all built-in prompts
    val TRANSLATE_UI_LANGUAGE_ID = stableId("translate-ui-language")
    val SUMMARY_ID = stableId("summary")
    val EXPLAIN_VERSES_ID = stableId("explain-verses")
    val STRONGS_ANNOTATION_ID = stableId("strongs-annotation")
    val WORD_STUDY_ID = stableId("word-study")

    // Test prompt IDs
    val TEST_TOOL_CALLING_ID = stableId("test-tool-calling")
    val TEST_CROSS_REFERENCES_ID = stableId("test-cross-references")
    val TEST_CREATE_BOOKMARK_ID = stableId("test-create-bookmark")
    val TEST_SEARCH_BIBLE_ID = stableId("test-search-bible")
    val TEST_COMMENTARY_ID = stableId("test-commentary")
    val TEST_DICTIONARY_ID = stableId("test-dictionary")
    val TEST_READ_BOOKMARKS_ID = stableId("test-read-bookmarks")
    val TEST_LABELS_ID = stableId("test-labels")
    val TEST_STUDYPAD_ID = stableId("test-studypad")
    val TEST_FINISH_STUDYPAD_ID = stableId("test-finish-studypad")
    val TEST_UPDATE_NOTE_ID = stableId("test-update-note")
    val TEST_STUDYPAD_READ_MODES_ID = stableId("test-studypad-read-modes")
    val TEST_REGENERATE_ID = stableId("test-regenerate")
    val TEST_CAPITALIZE_ID = stableId("test-capitalize")

    private fun getUiLanguageName(): String {
        val locale = Locale.getDefault()
        return locale.getDisplayLanguage(locale)
    }

    /**
     * Returns all built-in prompts as AgentPrompt objects.
     *
     * Display names are localized. Prompt templates use generic language references
     * (e.g. "user's UI language") since PromptProcessor system prompt already provides
     * the concrete UI language.
     */
    fun allBuiltInPrompts(): List<AgentPrompt> = productionPrompts() + testPrompts()

    private val _productionPrompts by lazy { buildProductionPrompts() }
    private val _testPrompts by lazy { buildTestPrompts() }

    /**
     * Returns only production (non-test) built-in prompts.
     */
    fun productionPrompts(): List<AgentPrompt> = _productionPrompts

    private fun buildProductionPrompts(): List<AgentPrompt> {
        val context = BibleApplication.application
        var order = 0

        return listOf(
            AgentPrompt(
                id = TRANSLATE_UI_LANGUAGE_ID,
                name = context.getString(R.string.default_prompt_translate_to_language, getUiLanguageName()),
                description = context.getString(R.string.default_prompt_translate_to_ui_language_desc),
                promptTemplate = """
                    Translate the following text to ${getUiLanguageName()}.

                    You MAY use getInstalledDocuments to check if a ${getUiLanguageName()} Bible translation is installed.
                    If one exists, use getVerseContent to get the text from that translation.
                    If NO ${getUiLanguageName()} translation is installed, translate the text yourself directly.

                    Do not add any explanations or commentary.
                """.trimIndent(),
                showIn = setOf(
                    PromptContext.TEXT_DISPLAY_SETTINGS,
                    PromptContext.VERSE_SELECTION,
                    PromptContext.WINDOW_MENU
                ),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = SUMMARY_ID,
                name = context.getString(R.string.default_prompt_summary),
                description = context.getString(R.string.default_prompt_summary_desc),
                promptTemplate = """
                    Create a concise summary of the selected text.
                """.trimIndent(),
                showIn = setOf(
                    PromptContext.TEXT_DISPLAY_SETTINGS,
                    PromptContext.VERSE_SELECTION,
                    PromptContext.WINDOW_MENU
                ),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = EXPLAIN_VERSES_ID,
                name = context.getString(R.string.default_prompt_explain_verses),
                description = context.getString(R.string.default_prompt_explain_verses_desc),
                promptTemplate = """
                    Explain the meaning and context of the selected verses.
                    Do not make up your own ideas and interpretations but use available 
                    commentaries as a reference. 
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = STRONGS_ANNOTATION_ID,
                name = context.getString(R.string.default_prompt_strongs_annotation),
                description = context.getString(R.string.default_prompt_strongs_annotation_desc),
                promptTemplate = """
                    Annotate each word in the Bible text with Strong's concordance numbers.

                    Steps:
                    1. Use getVerseContent to read the same passage from "KJV" (has Strong's numbers)
                    2. The KJV text has <w lemma="strong:HXXXX"> tags mapping words to Strong's numbers
                    3. Map each word/phrase in the source text to the corresponding Strong's number
                    4. Wrap annotated words: <w lemma="strong:XXXX">word</w>
                    5. Multiple Strong's: <w lemma="strong:H1234 strong:H5678">word</w>
                    6. No clear mapping: leave word unwrapped
                    7. Preserve ALL XML structure, attributes, verse tags exactly
                    8. Do not translate or change text content

                    Example:
                    Source: <verse osisID="Gen.1.1">Alussa Jumala loi taivaan ja maan.</verse>
                    After getVerseContent("KJV", "Gen.1.1"): <w lemma="strong:H7225">In the beginning</w> <w lemma="strong:H0430">God</w>...
                    Result: <verse osisID="Gen.1.1"><w lemma="strong:H7225">Alussa</w> <w lemma="strong:H0430">Jumala</w>...
                """.trimIndent(),
                showIn = setOf(PromptContext.TEXT_DISPLAY_SETTINGS),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = WORD_STUDY_ID,
                name = context.getString(R.string.default_prompt_word_study),
                description = context.getString(R.string.default_prompt_word_study_desc),
                promptTemplate = """
                    Analyze the original Hebrew/Greek words in the selected text.
                    If Strongs numbers are available, use getDictionaryEntry to look up definitions.
                    Include links to dictionary entries: [Strong's G2316](sword://StrongsGreek/G2316)
                    Cite each dictionary source by name when referencing definitions.
                    Explain the etymology, usage, and theological significance of key terms.
                """.trimIndent(),
                showIn = setOf(
                    PromptContext.VERSE_SELECTION,
                    PromptContext.TEXT_SELECTION
                ),
                orderNumber = order++,
                strictContextMatching = false,
            ),
        )
    }

    /**
     * Returns test prompts (visible only in debug mode).
     * These have a 🧪 prefix in their names.
     */
    fun testPrompts(): List<AgentPrompt> = _testPrompts

    private fun buildTestPrompts(): List<AgentPrompt> {
        var order = 100 // Start at 100 to keep them after production prompts

        return listOf(
            AgentPrompt(
                id = TEST_TOOL_CALLING_ID,
                name = "\uD83E\uDDEA Test: Tool Calling",
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

            AgentPrompt(
                id = TEST_CROSS_REFERENCES_ID,
                name = "\uD83E\uDDEA Test: Cross-References",
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

            AgentPrompt(
                id = TEST_CREATE_BOOKMARK_ID,
                name = "\uD83E\uDDEA Test: Create Bookmark",
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

            AgentPrompt(
                id = TEST_SEARCH_BIBLE_ID,
                name = "\uD83E\uDDEA Test: Search Bible",
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

            AgentPrompt(
                id = TEST_COMMENTARY_ID,
                name = "\uD83E\uDDEA Test: Commentary",
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

            AgentPrompt(
                id = TEST_DICTIONARY_ID,
                name = "\uD83E\uDDEA Test: Dictionary",
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

            AgentPrompt(
                id = TEST_READ_BOOKMARKS_ID,
                name = "\uD83E\uDDEA Test: Read Bookmarks",
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

            AgentPrompt(
                id = TEST_LABELS_ID,
                name = "\uD83E\uDDEA Test: Labels",
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

            AgentPrompt(
                id = TEST_STUDYPAD_ID,
                name = "\uD83E\uDDEA Test: StudyPad",
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

            AgentPrompt(
                id = TEST_FINISH_STUDYPAD_ID,
                name = "\uD83E\uDDEA Test: Finish with StudyPad",
                description = "Test creating a StudyPad and opening it as the result",
                promptTemplate = """
                    This is a test prompt. Please:
                    1. Create a new label/StudyPad using createLabel with name "AI Study Notes" (or add a unique suffix if it already exists)
                    2. Create a bookmark for the selected verses using createBookmark
                    3. Add the new label to the bookmark using addLabelToBookmark (so the bookmark appears in the StudyPad)
                    4. Add 1-2 text entries to the StudyPad using addStudyPadEntry with brief commentary about the selected verses
                    5. Call finishWithStudyPad with the label ID and a message like "Created study notes"

                    IMPORTANT: Do NOT use setDocumentTitle. Use finishWithStudyPad to open the StudyPad directly.
                    This tests the StudyPad-as-result flow instead of creating an AI document.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = TEST_UPDATE_NOTE_ID,
                name = "\uD83E\uDDEA Test: Update Note",
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

            AgentPrompt(
                id = TEST_STUDYPAD_READ_MODES_ID,
                name = "\uD83E\uDDEA Test: StudyPad Read Modes",
                description = "Test StudyPad read modes: info, index, page",
                promptTemplate = """
                    This is a test prompt for StudyPad read modes. Please:
                    1. Use getAllLabels to find a StudyPad that has content (or create one with a few entries first)
                    2. Use getStudyPadContent with mode='info' to get metadata (entry counts, estimated size)
                    3. Use getStudyPadContent with mode='index' to get the lightweight entry index
                    4. Use getStudyPadContent with mode='page' with offset=0, limit=2 to read the first 2 entries
                    5. If there are more entries, use mode='page' with offset=2, limit=2 to read the next page
                    6. Compare: use mode='full' to get all content at once
                    7. Report all results clearly, showing the differences between each mode's output

                    This tests that all four read modes work correctly and return the expected format.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = TEST_REGENERATE_ID,
                name = "\uD83E\uDDEA Test: Regenerate",
                description = "Simple prompt for testing regeneration",
                promptTemplate = """
                    Write a brief reflection on the selected verses.
                    Keep it to 2-3 sentences.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = TEST_CAPITALIZE_ID,
                name = "\uD83E\uDDEA Test: Capitalize First Words",
                description = "Simple fast test: capitalize first word of each sentence",
                promptTemplate = """
                    Capitalize the FIRST WORD of each sentence entirely (all letters uppercase).
                    Only the first word of each sentence should be fully capitalized, leave the rest as-is.
                    Do not add any explanations. Just return the modified text.

                    Example: "In the beginning God created" → "IN the beginning God created"
                """.trimIndent(),
                showIn = setOf(PromptContext.TEXT_DISPLAY_SETTINGS, PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),
        )
    }

    /** Set of all built-in prompt IDs for quick lookup. */
    private val builtInIds: Set<IdType> by lazy {
        allBuiltInPrompts().map { it.id }.toSet()
    }

    /** Check if a given ID belongs to a built-in prompt. */
    fun isBuiltIn(id: IdType): Boolean = id in builtInIds

    /** Set of test prompt IDs for quick lookup. */
    private val testIds: Set<IdType> by lazy {
        testPrompts().map { it.id }.toSet()
    }

    /** Check if a given ID belongs to a test (debug-only) prompt. */
    fun isTestPrompt(id: IdType): Boolean = id in testIds

    /** Get a built-in prompt by ID, or null if not found. */
    fun promptById(id: IdType): AgentPrompt? = allBuiltInPrompts().find { it.id == id }
}
