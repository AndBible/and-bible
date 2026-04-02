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
import net.bible.service.llm.agent.PermissionMode
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.common.CommonUtils
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
    val EXPLAIN_VERSES_STUDYPAD_ID = stableId("explain-verses-studypad")
    val STRONGS_ANNOTATION_ID = stableId("strongs-annotation")
    val WORD_STUDY_ID = stableId("word-study")
    val CROSS_REFERENCES_ID = stableId("cross-references")
    val COMPARE_TRANSLATIONS_ID = stableId("compare-translations")
    val THEMATIC_STUDY_ID = stableId("thematic-study")

    val BOOKMARK_ANNOTATE_ID = stableId("bookmark-annotate")
    val STUDY_LAYOUT_ID = stableId("study-layout")
    val WORKSPACE_ASSISTANT_ID = stableId("workspace-assistant")
    val ENHANCE_NOTE_ID = stableId("enhance-note")
    val ASK_QUESTION_ID = stableId("ask-question")
    val CUSTOM_PROMPT_ID = stableId("custom-prompt")

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
    val TEST_MY_DOCUMENTS_ID = stableId("test-my-documents")

    private fun getUiLanguageName(): String =
        CommonUtils.aiSettings.aiDisplayLanguage

    /**
     * Returns all built-in prompts as AgentPrompt objects.
     *
     * Display names are localized. Prompt templates use generic language references
     * (e.g. "user's UI language") since PromptProcessor system prompt already provides
     * the concrete UI language.
     */
    fun allBuiltInPrompts(): List<AgentPrompt> = productionPrompts() + testPrompts()

    /**
     * Returns only production (non-test) built-in prompts.
     * Not cached because prompt names depend on the configurable AI language.
     */
    fun productionPrompts(): List<AgentPrompt> = buildProductionPrompts()

    /**
     * Computes the deny set for a given allow set: all non-structural tools NOT in [allowed].
     * Used by built-in prompts to restrict tool visibility via [AgentPrompt.deniedTools].
     */
    private fun denyExcept(allowed: Set<AgentTool>): Set<AgentTool> =
        AgentTool.entries.toSet() - allowed - ToolRegistry.STRUCTURAL_TOOLS

    /** Bible content read tools — the core set for most read-only prompts. */
    private val BIBLE_READ_TOOLS = setOf(
        AgentTool.GET_VERSE_CONTENT,
        AgentTool.SEARCH_BIBLE,
        AgentTool.GET_COMMENTARIES,
        AgentTool.GET_INSTALLED_DOCUMENTS,
    )

    /** Extended Bible tools including Strong's and dictionaries. */
    private val BIBLE_STUDY_TOOLS = BIBLE_READ_TOOLS + setOf(
        AgentTool.SEARCH_BY_STRONGS,
        AgentTool.GET_DICTIONARY_ENTRY,
    )

    private fun buildProductionPrompts(): List<AgentPrompt> {
        val context = BibleApplication.application
        var order = 0

        return listOf(
            // 1. Translate
            AgentPrompt(
                id = TRANSLATE_UI_LANGUAGE_ID,
                name = context.getString(R.string.default_prompt_translate_to_language, getUiLanguageName()),
                description = context.getString(R.string.default_prompt_translate_to_ui_language_desc),
                promptTemplate = """
                    Translate the selected text to ${getUiLanguageName()}.
                    If the user has highlighted or selected a specific portion, translate ONLY that portion.
                    Aim for accuracy over literary style.
                    Do not add explanations or commentary.
                    Output only the translated text.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                isTextTransformation = true,
            ),

            // 2. Summary
            AgentPrompt(
                id = SUMMARY_ID,
                name = context.getString(R.string.default_prompt_summary),
                description = context.getString(R.string.default_prompt_summary_desc),
                promptTemplate = """
                    Create a concise summary of the selected passage.
                    If the user has highlighted or selected a specific portion, focus your summary on that portion.

                    Structure your summary as:
                    1. **Context** — Brief historical/literary context (1-2 sentences)
                    2. **Main Points** — Key themes and teachings (bullet points)
                    3. **Significance** — Why this passage matters (1-2 sentences)

                    Keep the total length to 150-300 words.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                allowedTools = emptySet(),
                deniedTools = denyExcept(emptySet()),
            ),

            // 3. Explain Verses
            AgentPrompt(
                id = EXPLAIN_VERSES_ID,
                name = context.getString(R.string.default_prompt_explain_verses),
                description = context.getString(R.string.default_prompt_explain_verses_desc),
                promptTemplate = """
                    Explain the meaning and context of the selected verses.

                    APPROACH:
                    Installed documents and commentaries for the selected verses are provided below.
                    Synthesize the commentary perspectives into a clear explanation.
                    If Strong's dictionaries are available, use getDictionaryEntry for key theological terms.

                    STRUCTURE your explanation:
                    - **Historical Context** — Who wrote this, to whom, and when
                    - **Verse-by-Verse Explanation** — Walk through the passage
                    - **Key Themes** — Major theological themes
                    - **Application** — How this applies today

                    Base your explanation on the provided commentaries. Cite each source by name.
                    Do not invent interpretations — ground everything in the available reference works.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                bibleOnly = true,
                allowedTools = BIBLE_STUDY_TOOLS,
                deniedTools = denyExcept(BIBLE_STUDY_TOOLS),
                autoIncludeDocuments = true,
                autoIncludeCommentaries = true,
            ),

            // 3b. Explain Verses → StudyPad
            AgentPrompt(
                id = EXPLAIN_VERSES_STUDYPAD_ID,
                name = context.getString(R.string.default_prompt_explain_verses_studypad),
                description = context.getString(R.string.default_prompt_explain_verses_studypad_desc),
                promptTemplate = """
                    Explain the selected verses and create a StudyPad with the explanation.

                    APPROACH:
                    Installed documents and commentaries for the selected verses are provided below.
                    If Strong's dictionaries are available, use getDictionaryEntry for key theological terms.
                    Build a StudyPad using createStudyPad with these items in order:
                       - A text entry with historical context (who wrote this, to whom, when)
                       - For each verse or small group of verses:
                         a. A bookmark to the verse(s)
                         b. A text entry explaining that verse, citing commentaries by name
                       - A text entry summarizing key themes
                       - A text entry with application for today
                    5. Call finishWithStudyPad with the returned labelId to open it.

                    Base your explanation on the provided commentaries.
                    Do not invent interpretations — ground everything in the available reference works.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                bibleOnly = true,
                strictContextMatching = false,
                permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
                allowedTools = BIBLE_STUDY_TOOLS + setOf(
                    AgentTool.CREATE_STUDY_PAD,
                ),
                deniedTools = denyExcept(BIBLE_STUDY_TOOLS + setOf(
                    AgentTool.CREATE_STUDY_PAD,
                )),
                autoIncludeDocuments = true,
                autoIncludeCommentaries = true,
            ),

            // 4. Word Study
            AgentPrompt(
                id = WORD_STUDY_ID,
                name = context.getString(R.string.default_prompt_word_study),
                description = context.getString(R.string.default_prompt_word_study_desc),
                promptTemplate = """
                    Perform a word study on the original Hebrew/Greek words in the selected text.

                    APPROACH:
                    Installed documents are provided below.
                    1. Use getVerseContent with osis=true to retrieve text with Strong's markup.
                    3. For each key word, use getDictionaryEntry to look up its Strong's number.
                    4. Use searchByStrongs to find other passages where the same word appears.

                    STRUCTURE your study per key word (3-5 words max):
                    - **Original word** — Hebrew/Greek form, transliteration, Strong's number with link [Strong's GXXXX](strongs://GXXXX)
                    - **Definition** — Full dictionary definition (cite the source by name)
                    - **Usage in this passage** — How the word functions here
                    - **Other occurrences** — 3-5 notable passages using this word (with links)
                    - **Theological significance** — Key insights

                    Focus on the most theologically significant words.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.TEXT_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                bibleOnly = true,
                strictContextMatching = false,
                allowedTools = BIBLE_STUDY_TOOLS,
                deniedTools = denyExcept(BIBLE_STUDY_TOOLS),
                autoIncludeDocuments = true,
            ),

            // 5. Cross-References
            AgentPrompt(
                id = CROSS_REFERENCES_ID,
                name = context.getString(R.string.default_prompt_cross_references),
                description = context.getString(R.string.default_prompt_cross_references_desc),
                promptTemplate = """
                    Find and explain cross-references for the selected verses.

                    APPROACH:
                    Commentaries for the selected verses are provided below.
                    1. Use searchBible to find passages with shared keywords and themes.
                    2. Check the provided commentaries for passages they mention as related.

                    GROUP cross-references by connection type:
                    - **Direct Quotes/Allusions** — Where this passage quotes or echoes another
                    - **Parallel Passages** — Similar accounts or teachings elsewhere
                    - **Thematic Connections** — Passages sharing the same theme
                    - **Fulfillment/Prophecy** — Prophetic connections

                    For each cross-reference, provide a clickable link and a brief explanation (1-2 sentences).
                    Aim for 8-15 cross-references, prioritizing the most significant connections.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                bibleOnly = true,
                strictContextMatching = false,
                allowedTools = BIBLE_READ_TOOLS,
                deniedTools = denyExcept(BIBLE_READ_TOOLS),
                autoIncludeCommentaries = true,
            ),

            // 6. Compare Translations
            AgentPrompt(
                id = COMPARE_TRANSLATIONS_ID,
                name = context.getString(R.string.default_prompt_compare_translations),
                description = context.getString(R.string.default_prompt_compare_translations_desc),
                promptTemplate = """
                    Compare how different Bible translations render the selected verses.

                    APPROACH:
                    Installed documents are provided below.
                    1. Use getVerseContent to retrieve the selected passage from each installed Bible translation.
                    3. Compare the translations side by side.

                    STRUCTURE:
                    - List each translation's rendering with the translation name as a heading.
                    - After listing all translations, provide a **Key Differences** section highlighting:
                      - Significant wording differences and what they mean
                      - Where translations disagree on meaning (not just style)
                      - Which textual traditions or manuscripts may explain differences

                    If Strong's dictionaries are available, reference the original language
                    where it helps explain why translations differ.
                    Do not editorialize about which translation is "better."
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                bibleOnly = true,
                strictContextMatching = false,
                allowedTools = setOf(
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.GET_DICTIONARY_ENTRY,
                    AgentTool.SEARCH_BIBLE,
                ),
                deniedTools = denyExcept(setOf(
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.GET_DICTIONARY_ENTRY,
                    AgentTool.SEARCH_BIBLE,
                )),
                autoIncludeDocuments = true,
            ),

            // 7. Thematic Study → StudyPad
            AgentPrompt(
                id = THEMATIC_STUDY_ID,
                name = context.getString(R.string.default_prompt_thematic_study),
                description = context.getString(R.string.default_prompt_thematic_study_desc),
                promptTemplate = """
                    Build a thematic study based on the selected passage.

                    APPROACH:
                    1. Identify the primary theme (e.g., "God's faithfulness", "prayer", "forgiveness").
                    2. Identify 8-12 passages related to this theme using your Bible knowledge.
                       You may use searchBible to supplement, but for thematic connections your own
                       knowledge of Scripture is usually more effective than keyword search.
                       If you do search, use the indexed Bible's language (see system context).
                    3. Use getVerseContent to retrieve each passage from the active document.
                    4. Use the provided commentaries (included below) to add depth to 2-3 key passages.
                    5. Build a StudyPad using createStudyPad with a descriptive name
                       (e.g., "Thematic Study: God's Faithfulness") and items:
                       - A text entry with an introduction to the theme
                       - For each key passage: a bookmark with a note explaining its relevance
                       - A text entry with concluding thoughts
                    6. Call finishWithStudyPad with the returned labelId to open it.

                    Organize passages in a logical progression (e.g., Old Testament → New Testament).
                    Include 8-12 passages total.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                bibleOnly = true,
                strictContextMatching = false,
                permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
                allowedTools = BIBLE_READ_TOOLS + setOf(
                    AgentTool.CREATE_STUDY_PAD,
                    AgentTool.GET_ALL_LABELS,
                    AgentTool.GET_BOOKMARKS_FOR_VERSE,
                ),
                deniedTools = denyExcept(BIBLE_READ_TOOLS + setOf(
                    AgentTool.CREATE_STUDY_PAD,
                    AgentTool.GET_ALL_LABELS,
                    AgentTool.GET_BOOKMARKS_FOR_VERSE,
                )),
                autoIncludeDocuments = true,
                autoIncludeCommentaries = true,
            ),

            // 8. Bookmark & Annotate
            AgentPrompt(
                id = BOOKMARK_ANNOTATE_ID,
                name = context.getString(R.string.default_prompt_bookmark_annotate),
                description = context.getString(R.string.default_prompt_bookmark_annotate_desc),
                promptTemplate = """
                    Create a bookmark for the selected verses and add a study note.

                    APPROACH:
                    Commentaries for the selected verses are provided below (if available).
                    1. Create a bookmark using createBookmark for the selected verses.
                    3. Write a concise study note (3-5 sentences) covering:
                       - What this passage is about
                       - Key insight or takeaway
                       - A related cross-reference
                    4. Use addBookmarkNote to attach the note to the bookmark.
                    5. Call finishWithoutDocument with a confirmation message.

                    Keep the note concise and useful for future reference.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                bibleOnly = true,
                noDocumentCreation = true,
                permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
                allowedTools = setOf(
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.GET_COMMENTARIES,
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.CREATE_BOOKMARK,
                    AgentTool.ADD_BOOKMARK_NOTE,
                    AgentTool.GET_BOOKMARKS_FOR_VERSE,
                ),
                deniedTools = denyExcept(setOf(
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.GET_COMMENTARIES,
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.CREATE_BOOKMARK,
                    AgentTool.ADD_BOOKMARK_NOTE,
                    AgentTool.GET_BOOKMARKS_FOR_VERSE,
                )),
                autoIncludeDocuments = true,
                autoIncludeCommentaries = true,
            ),

            // 10. Open Study Layout
            AgentPrompt(
                id = STUDY_LAYOUT_ID,
                name = context.getString(R.string.default_prompt_study_layout),
                description = context.getString(R.string.default_prompt_study_layout_desc),
                promptTemplate = """
                    Set up a multi-window study layout for the selected passage.

                    APPROACH:
                    1. Use getInstalledDocuments to find available Bibles, commentaries, and dictionaries.
                    2. Use getWindows to see the current window layout.
                    3. Set up an optimal study layout:
                       a. Ensure the current window shows the selected passage.
                       b. If a commentary is installed, use createWindow to open a commentary window on the same passage.
                       c. If another Bible translation is installed, use createWindow to open a parallel translation window.
                    4. Call finishWithoutDocument confirming what layout was created.

                    Create at most 3 windows total (including existing ones) to avoid cluttering the screen.
                    Prefer: 1 Bible + 1 Commentary, or 2 Bibles + 1 Commentary.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU, PromptContext.WORKSPACE_MENU),
                orderNumber = order++,
                noDocumentCreation = true,
                permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
                allowedTools = setOf(
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.GET_WINDOWS,
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.CREATE_WINDOW,
                    AgentTool.MANAGE_WINDOW,
                    AgentTool.SET_WINDOW_DOCUMENT,
                ),
                deniedTools = denyExcept(setOf(
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.GET_WINDOWS,
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.CREATE_WINDOW,
                    AgentTool.MANAGE_WINDOW,
                    AgentTool.SET_WINDOW_DOCUMENT,
                )),
                autoIncludeDocuments = true,
            ),

            // 11. Workspace Assistant
            AgentPrompt(
                id = WORKSPACE_ASSISTANT_ID,
                name = context.getString(R.string.default_prompt_workspace_assistant),
                description = context.getString(R.string.default_prompt_workspace_assistant_desc),
                promptTemplate = """
                    Help the user manage their workspace windows.
                    The current workspace layout is provided in the system prompt.

                    You can:
                    - Rearrange, create, close, or minimize windows
                    - Change documents shown in windows
                    - Set up study layouts with multiple translations and commentaries

                    User will tell you what they'd like to do.
                    Use getWindows and getInstalledDocuments to understand the current state,
                    then use createWindow, manageWindow, and setWindowDocument as needed.
                    When done, call finishWithoutDocument with a summary of changes made.
                """.trimIndent(),
                showIn = setOf(PromptContext.WORKSPACE_MENU),
                orderNumber = order++,
                noDocumentCreation = true,
                specifyBeforeRun = true,
                permissionMode = PermissionMode.ASK_ONCE_PER_RUN,
                allowedTools = setOf(
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.GET_WINDOWS,
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.CREATE_WINDOW,
                    AgentTool.MANAGE_WINDOW,
                    AgentTool.SET_WINDOW_DOCUMENT,
                ),
                deniedTools = denyExcept(setOf(
                    AgentTool.GET_INSTALLED_DOCUMENTS,
                    AgentTool.GET_WINDOWS,
                    AgentTool.GET_VERSE_CONTENT,
                    AgentTool.CREATE_WINDOW,
                    AgentTool.MANAGE_WINDOW,
                    AgentTool.SET_WINDOW_DOCUMENT,
                )),
                autoIncludeDocuments = true,
            ),

            // 12. Enhance Note
            AgentPrompt(
                id = ENHANCE_NOTE_ID,
                name = context.getString(R.string.default_prompt_enhance_note),
                description = context.getString(R.string.default_prompt_enhance_note_desc),
                promptTemplate = """
                    Improve the language and clarity of the user's note.

                    APPROACH:
                    - Fix grammar, spelling, and punctuation errors
                    - Improve sentence structure and readability
                    - Make the writing more concise where appropriate
                    - Preserve the original meaning and intent

                    IMPORTANT: Preserve the user's original thoughts, voice, and content.
                    Only improve the language — do not add new content, commentary, or cross-references.
                """.trimIndent(),
                showIn = setOf(PromptContext.NOTE_EDITOR),
                orderNumber = order++,
                isTextTransformation = true,
            ),

            // 12. Ask a Question
            AgentPrompt(
                id = ASK_QUESTION_ID,
                name = context.getString(R.string.default_prompt_ask_question),
                description = context.getString(R.string.default_prompt_ask_question_desc),
                promptTemplate = """
                    Answer the user's question about the selected passage.
                    Commentaries and installed documents are provided below.
                    Use them to provide a well-sourced answer.
                    Cite your sources and include clickable Bible reference links.
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                specifyBeforeRun = true,
                allowedTools = BIBLE_STUDY_TOOLS,
                deniedTools = denyExcept(BIBLE_STUDY_TOOLS),
                autoIncludeDocuments = true,
                autoIncludeCommentaries = true,
            ),

            // 13. Custom Prompt
            AgentPrompt(
                id = CUSTOM_PROMPT_ID,
                name = context.getString(R.string.default_prompt_custom),
                description = context.getString(R.string.default_prompt_custom_desc),
                promptTemplate = context.getString(R.string.default_prompt_custom_template),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
                orderNumber = order++,
                specifyBeforeRun = true,
            ),
        )
    }

    /**
     * Returns test prompts (visible only in debug mode).
     * These have a 🧪 prefix in their names.
     */
    fun testPrompts(): List<AgentPrompt> = buildTestPrompts()

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
                showIn = setOf(PromptContext.VERSE_SELECTION),
                orderNumber = order++,
            ),

            AgentPrompt(
                id = TEST_MY_DOCUMENTS_ID,
                name = "\uD83E\uDDEA Test: My Documents",
                description = "Test My Documents CRUD operations",
                promptTemplate = """
                    This is a test prompt for My Documents tools. Please:
                    1. Use getMyDocuments to list all document books (note the AI Documents book details)
                    2. Add a new page to the AI Documents book using addMyDocumentPage with a brief note about the selected verses
                    3. Use getMyDocumentPages with initials='AIDocuments' to verify the page was created
                    4. Edit the page title using editMyDocumentPage
                    5. Call finishWithMyDocumentPage with the page ID to open the created page
                """.trimIndent(),
                showIn = setOf(PromptContext.VERSE_SELECTION, PromptContext.WINDOW_MENU),
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

    // --- Default categories for built-in prompts ---

    /** Stable category IDs used as defaults for built-in prompts. */
    val CATEGORY_STUDY_ID = stableId("category-study")
    val CATEGORY_NOTES_ID = stableId("category-notes")
    val CATEGORY_GENERAL_ID = stableId("category-general")
    val CATEGORY_TEST_ID = stableId("category-test")

    /** Default categories with localized names. Cached — only rebuilt on first access. */
    private var _defaultCategories: List<PromptCategory>? = null
    fun defaultCategories(): List<PromptCategory> {
        _defaultCategories?.let { return it }
        val ctx = BibleApplication.application
        return buildList {
            add(PromptCategory(id = CATEGORY_STUDY_ID, name = ctx.getString(R.string.prompt_category_study), orderNumber = 0))
            add(PromptCategory(id = CATEGORY_NOTES_ID, name = ctx.getString(R.string.prompt_category_notes), orderNumber = 1))
            add(PromptCategory(id = CATEGORY_GENERAL_ID, name = ctx.getString(R.string.prompt_category_general), orderNumber = 2))
            if (CommonUtils.isDebugMode) {
                add(PromptCategory(id = CATEGORY_TEST_ID, name = ctx.getString(R.string.prompt_category_test), orderNumber = 3))
            }
        }.also { _defaultCategories = it }
    }

    private val defaultCategoryMap: Map<IdType, IdType> by lazy {
        mapOf(
            // Study
            EXPLAIN_VERSES_ID to CATEGORY_STUDY_ID,
            EXPLAIN_VERSES_STUDYPAD_ID to CATEGORY_STUDY_ID,
            STRONGS_ANNOTATION_ID to CATEGORY_STUDY_ID,
            WORD_STUDY_ID to CATEGORY_STUDY_ID,
            CROSS_REFERENCES_ID to CATEGORY_STUDY_ID,
            COMPARE_TRANSLATIONS_ID to CATEGORY_STUDY_ID,
            THEMATIC_STUDY_ID to CATEGORY_STUDY_ID,
            // Notes
            BOOKMARK_ANNOTATE_ID to CATEGORY_NOTES_ID,
            ENHANCE_NOTE_ID to CATEGORY_NOTES_ID,
            STUDY_LAYOUT_ID to CATEGORY_NOTES_ID,
            // General
            TRANSLATE_UI_LANGUAGE_ID to CATEGORY_GENERAL_ID,
            SUMMARY_ID to CATEGORY_GENERAL_ID,

            ASK_QUESTION_ID to CATEGORY_GENERAL_ID,
            CUSTOM_PROMPT_ID to CATEGORY_GENERAL_ID,
            WORKSPACE_ASSISTANT_ID to CATEGORY_GENERAL_ID,
            // Test
            TEST_TOOL_CALLING_ID to CATEGORY_TEST_ID,
            TEST_CROSS_REFERENCES_ID to CATEGORY_TEST_ID,
            TEST_CREATE_BOOKMARK_ID to CATEGORY_TEST_ID,
            TEST_SEARCH_BIBLE_ID to CATEGORY_TEST_ID,
            TEST_COMMENTARY_ID to CATEGORY_TEST_ID,
            TEST_DICTIONARY_ID to CATEGORY_TEST_ID,
            TEST_READ_BOOKMARKS_ID to CATEGORY_TEST_ID,
            TEST_LABELS_ID to CATEGORY_TEST_ID,
            TEST_STUDYPAD_ID to CATEGORY_TEST_ID,
            TEST_FINISH_STUDYPAD_ID to CATEGORY_TEST_ID,
            TEST_UPDATE_NOTE_ID to CATEGORY_TEST_ID,
            TEST_STUDYPAD_READ_MODES_ID to CATEGORY_TEST_ID,
            TEST_REGENERATE_ID to CATEGORY_TEST_ID,
            TEST_CAPITALIZE_ID to CATEGORY_TEST_ID,
            TEST_MY_DOCUMENTS_ID to CATEGORY_TEST_ID,
        )
    }

    /** Check if a category ID belongs to a built-in category. */
    fun isBuiltInCategory(id: IdType): Boolean = defaultCategories().any { it.id == id }

    /** Returns the default category ID for a built-in prompt, or null if uncategorized. */
    fun defaultCategoryForPrompt(id: IdType): IdType? = defaultCategoryMap[id]
}
