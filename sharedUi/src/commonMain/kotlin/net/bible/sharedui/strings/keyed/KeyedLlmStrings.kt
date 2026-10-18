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

package net.bible.sharedui.strings.keyed

import net.bible.service.llm.AgentTool
import net.bible.service.llm.LlmStrings

/** [LlmStrings] over a [StringTable]; mirrors `AndroidLlmStrings`. */
class KeyedLlmStrings(private val t: StringTable) : LlmStrings {
    override val actionActiveWindow: String get() = t.text("action_active_window")
    override fun actionAddEntryToStudypad(a1: String): String = t.fmt("action_add_entry_to_studypad", a1)
    override fun actionAddLabelToBookmark(a1: String, a2: String): String = t.fmt("action_add_label_to_bookmark", a1, a2)
    override fun actionAddMyDocumentPage(a1: String, a2: String): String = t.fmt("action_add_my_document_page", a1, a2)
    override fun actionAddNoteToBookmarkAt(a1: String): String = t.fmt("action_add_note_to_bookmark_at", a1)
    override fun actionCloseWindow(a1: String): String = t.fmt("action_close_window", a1)
    override fun actionCreateBookmarkAt(a1: String): String = t.fmt("action_create_bookmark_at", a1)
    override fun actionCreateBookmarkAtWithExtras(a1: String, a2: String): String = t.fmt("action_create_bookmark_at_with_extras", a1, a2)
    override fun actionCreateLabel(a1: String): String = t.fmt("action_create_label", a1)
    override fun actionCreateMyDocument(a1: String): String = t.fmt("action_create_my_document", a1)
    override fun actionCreateStudypad(a1: String, a2: Int): String = t.fmt("action_create_studypad", a1, a2)
    override val actionCreateWindow: String get() = t.text("action_create_window")
    override fun actionCreateWindowWithDoc(a1: String): String = t.fmt("action_create_window_with_doc", a1)
    override fun actionCreateWindowWithDocAndKey(a1: String, a2: String): String = t.fmt("action_create_window_with_doc_and_key", a1, a2)
    override fun actionDeleteBookmark(a1: String): String = t.fmt("action_delete_bookmark", a1)
    override fun actionDeleteLabel(a1: String): String = t.fmt("action_delete_label", a1)
    override fun actionDeleteMyDocumentPage(a1: String): String = t.fmt("action_delete_my_document_page", a1)
    override fun actionEditMyDocumentPage(a1: String): String = t.fmt("action_edit_my_document_page", a1)
    override fun actionFinish(a1: String): String = t.fmt("action_finish", a1)
    override fun actionLabels(a1: String): String = t.fmt("action_labels", a1)
    override fun actionMinimizeWindow(a1: String): String = t.fmt("action_minimize_window", a1)
    override fun actionOpenMyDocumentPage(a1: String): String = t.fmt("action_open_my_document_page", a1)
    override fun actionOpenStudypad(a1: String): String = t.fmt("action_open_studypad", a1)
    override fun actionRemoveLabelFromBookmark(a1: String, a2: String): String = t.fmt("action_remove_label_from_bookmark", a1, a2)
    override fun actionRestoreWindow(a1: String): String = t.fmt("action_restore_window", a1)
    override fun actionSetDocumentTitle(a1: String): String = t.fmt("action_set_document_title", a1)
    override fun actionSetWindowDocument(a1: String, a2: String): String = t.fmt("action_set_window_document", a1, a2)
    override fun actionSetWindowDocumentWithKey(a1: String, a2: String, a3: String): String = t.fmt("action_set_window_document_with_key", a1, a2, a3)
    override fun actionUpdateNoteOnBookmarkAt(a1: String): String = t.fmt("action_update_note_on_bookmark_at", a1)
    override fun actionUpdateStudypadTextEntry(a1: String): String = t.fmt("action_update_studypad_text_entry", a1)
    override val actionWithNote: String get() = t.text("action_with_note")
    override val agentAlreadyRunning: String get() = t.text("agent_already_running")
    override val agentLogCancelled: String get() = t.text("agent_log_cancelled")
    override val agentLogCompleted: String get() = t.text("agent_log_completed")
    override fun agentLogDone(a1: String): String = t.fmt("agent_log_done", a1)
    override fun agentLogExecuting(a1: String): String = t.fmt("agent_log_executing", a1)
    override fun agentLogIteration(a1: Int): String = t.fmt("agent_log_iteration", a1)
    override val agentLogResponseReceived: String get() = t.text("agent_log_response_received")
    override fun agentLogSaved(a1: String): String = t.fmt("agent_log_saved", a1)
    override fun agentLogTool(a1: String): String = t.fmt("agent_log_tool", a1)
    override fun agentLogToolFailed(a1: String): String = t.fmt("agent_log_tool_failed", a1)
    override val agentLogViewRaw: String get() = t.text("agent_log_view_raw")
    override val aiAgentNotificationCancel: String get() = t.text("ai_agent_notification_cancel")
    override val aiAgentNotificationCompleted: String get() = t.text("ai_agent_notification_completed")
    override val aiAgentNotificationCompletedTap: String get() = t.text("ai_agent_notification_completed_tap")
    override val aiAgentNotificationError: String get() = t.text("ai_agent_notification_error")
    override val aiAgentNotificationPermissionNeeded: String get() = t.text("ai_agent_notification_permission_needed")
    override val aiAgentNotificationPermissionTap: String get() = t.text("ai_agent_notification_permission_tap")
    override val aiAgentNotificationRunning: String get() = t.text("ai_agent_notification_running")
    override fun aiLanguageAppDefault(a1: String): String = t.fmt("ai_language_app_default", a1)
    override val aiRegeneratePromptNotFound: String get() = t.text("ai_regenerate_prompt_not_found")
    override val cancel: String get() = t.text("cancel")
    override fun commentaryFilterDialogMessage(a1: String): String = t.fmt("commentary_filter_dialog_message", a1)
    override val commentaryFilterDialogTitle: String get() = t.text("commentary_filter_dialog_title")
    override fun commentaryFilterItem(a1: String, a2: String, a3: String): String = t.fmt("commentary_filter_item", a1, a2, a3)
    override fun commentaryFilterTotalTokens(a1: String, a2: String): String = t.fmt("commentary_filter_total_tokens", a1, a2)
    override val defaultPromptAskQuestion: String get() = t.text("default_prompt_ask_question")
    override val defaultPromptAskQuestionDesc: String get() = t.text("default_prompt_ask_question_desc")
    override val defaultPromptBookmarkAnnotate: String get() = t.text("default_prompt_bookmark_annotate")
    override val defaultPromptBookmarkAnnotateDesc: String get() = t.text("default_prompt_bookmark_annotate_desc")
    override val defaultPromptCompareTranslations: String get() = t.text("default_prompt_compare_translations")
    override val defaultPromptCompareTranslationsDesc: String get() = t.text("default_prompt_compare_translations_desc")
    override val defaultPromptCrossReferences: String get() = t.text("default_prompt_cross_references")
    override val defaultPromptCrossReferencesDesc: String get() = t.text("default_prompt_cross_references_desc")
    override val defaultPromptCustom: String get() = t.text("default_prompt_custom")
    override val defaultPromptCustomDesc: String get() = t.text("default_prompt_custom_desc")
    override val defaultPromptCustomTemplate: String get() = t.text("default_prompt_custom_template")
    override val defaultPromptEnhanceNote: String get() = t.text("default_prompt_enhance_note")
    override val defaultPromptEnhanceNoteDesc: String get() = t.text("default_prompt_enhance_note_desc")
    override val defaultPromptExplainVerses: String get() = t.text("default_prompt_explain_verses")
    override val defaultPromptExplainVersesDesc: String get() = t.text("default_prompt_explain_verses_desc")
    override val defaultPromptExplainVersesStudypad: String get() = t.text("default_prompt_explain_verses_studypad")
    override val defaultPromptExplainVersesStudypadDesc: String get() = t.text("default_prompt_explain_verses_studypad_desc")
    override val defaultPromptStudyLayout: String get() = t.text("default_prompt_study_layout")
    override val defaultPromptStudyLayoutDesc: String get() = t.text("default_prompt_study_layout_desc")
    override val defaultPromptSummary: String get() = t.text("default_prompt_summary")
    override val defaultPromptSummaryDesc: String get() = t.text("default_prompt_summary_desc")
    override val defaultPromptThematicStudy: String get() = t.text("default_prompt_thematic_study")
    override val defaultPromptThematicStudyDesc: String get() = t.text("default_prompt_thematic_study_desc")
    override fun defaultPromptTranslateToLanguage(a1: String): String = t.fmt("default_prompt_translate_to_language", a1)
    override val defaultPromptTranslateToUiLanguageDesc: String get() = t.text("default_prompt_translate_to_ui_language_desc")
    override val defaultPromptWordStudy: String get() = t.text("default_prompt_word_study")
    override val defaultPromptWordStudyDesc: String get() = t.text("default_prompt_word_study_desc")
    override val defaultPromptWorkspaceAssistant: String get() = t.text("default_prompt_workspace_assistant")
    override val defaultPromptWorkspaceAssistantDesc: String get() = t.text("default_prompt_workspace_assistant_desc")
    override val easySetupAnthropicDesc: String get() = t.text("easy_setup_anthropic_desc")
    override val easySetupFreeTier: String get() = t.text("easy_setup_free_tier")
    override val easySetupGeminiDesc: String get() = t.text("easy_setup_gemini_desc")
    override val easySetupOpenaiDesc: String get() = t.text("easy_setup_openai_desc")
    override fun llmContinueIterationsMessage(a1: Int, a2: Int): String = t.fmt("llm_continue_iterations_message", a1, a2)
    override val llmContinueIterationsTitle: String get() = t.text("llm_continue_iterations_title")
    override fun llmCostTotal(a1: String): String = t.fmt("llm_cost_total", a1)
    override val llmDefaultDocumentTitle: String get() = t.text("llm_default_document_title")
    override val llmDefaultStudypadOpened: String get() = t.text("llm_default_studypad_opened")
    override val llmDefaultTaskCompleted: String get() = t.text("llm_default_task_completed")
    override fun llmErrorMaxIterations(a1: Int): String = t.fmt("llm_error_max_iterations", a1)
    override val llmErrorUnknown: String get() = t.text("llm_error_unknown")
    override val llmNoDocumentCreationIntercepted: String get() = t.text("llm_no_document_creation_intercepted")
    override val llmNoteUpdated: String get() = t.text("llm_note_updated")
    override fun llmParseError(a1: String): String = t.fmt("llm_parse_error", a1)
    override fun llmParseErrorAnthropic(a1: String): String = t.fmt("llm_parse_error_anthropic", a1)
    override val llmParseErrorNoChoices: String get() = t.text("llm_parse_error_no_choices")
    override fun llmParseErrorOpenai(a1: String): String = t.fmt("llm_parse_error_openai", a1)
    override val llmProviderCustom: String get() = t.text("llm_provider_custom")
    override fun llmRateLimitedRetrying(a1: String): String = t.fmt("llm_rate_limited_retrying", a1)
    override val llmUsageSummaryDefault: String get() = t.text("llm_usage_summary_default")
    override fun llmUsageSummaryFormat(a1: Long, a2: Long, a3: String): String = t.fmt("llm_usage_summary_format", a1, a2, a3)
    override val okay: String get() = t.text("okay")
    override fun permissionAlwaysAllowConfirm(a1: String): String = t.fmt("permission_always_allow_confirm", a1)
    override val permissionAlwaysAllowConfirmResetHint: String get() = t.text("permission_always_allow_confirm_reset_hint")
    override val permissionAlwaysAllowConfirmTitle: String get() = t.text("permission_always_allow_confirm_title")
    override val promptCategoryFavorites: String get() = t.text("prompt_category_favorites")
    override val promptCategoryGeneral: String get() = t.text("prompt_category_general")
    override val promptCategoryNotes: String get() = t.text("prompt_category_notes")
    override val promptCategoryStudy: String get() = t.text("prompt_category_study")
    override val promptCategoryTest: String get() = t.text("prompt_category_test")
    override val promptCategoryUncategorized: String get() = t.text("prompt_category_uncategorized")
    override val promptModelDefault: String get() = t.text("prompt_model_default")
    override fun rawLlmLogEntryApiResponse(a1: Int): String = t.fmt("raw_llm_log_entry_api_response", a1)
    override val rawLlmLogEntryAssistant: String get() = t.text("raw_llm_log_entry_assistant")
    override val rawLlmLogEntryEmpty: String get() = t.text("raw_llm_log_entry_empty")
    override val rawLlmLogEntrySystem: String get() = t.text("raw_llm_log_entry_system")
    override fun rawLlmLogEntryTokens(a1: String): String = t.fmt("raw_llm_log_entry_tokens", a1)
    override fun rawLlmLogEntryToolCall(a1: String): String = t.fmt("raw_llm_log_entry_tool_call", a1)
    override fun rawLlmLogEntryToolDefinitions(a1: Int): String = t.fmt("raw_llm_log_entry_tool_definitions", a1)
    override fun rawLlmLogEntryToolDesc(a1: String): String = t.fmt("raw_llm_log_entry_tool_desc", a1)
    override fun rawLlmLogEntryToolParams(a1: String): String = t.fmt("raw_llm_log_entry_tool_params", a1)
    override fun rawLlmLogEntryToolResult(a1: String): String = t.fmt("raw_llm_log_entry_tool_result", a1)
    override fun rawLlmLogEntryUsage(a1: String, a2: String, a3: String): String = t.fmt("raw_llm_log_entry_usage", a1, a2, a3)
    override val rawLlmLogEntryUser: String get() = t.text("raw_llm_log_entry_user")
    override fun rawLlmLogTotal(a1: String, a2: String, a3: String): String = t.fmt("raw_llm_log_total", a1, a2, a3)
    override fun rawLogItemTokens(a1: String, a2: String): String = t.fmt("raw_log_item_tokens", a1, a2)
    override val selectAll: String get() = t.text("select_all")
    override val selectNone: String get() = t.text("select_none")
    override val toolCategoryBibleSearch: String get() = t.text("tool_category_bible_search")
    override val toolCategoryBookmarks: String get() = t.text("tool_category_bookmarks")
    override val toolCategoryGeneralBooks: String get() = t.text("tool_category_general_books")
    override val toolCategoryLabels: String get() = t.text("tool_category_labels")
    override val toolCategoryMyDocuments: String get() = t.text("tool_category_my_documents")
    override val toolCategoryStudyPads: String get() = t.text("tool_category_study_pads")
    override val toolCategoryWindows: String get() = t.text("tool_category_windows")
    override fun toolLogBookmarkCount(a1: Int): String = t.fmt("tool_log_bookmark_count", a1)
    override fun toolLogCommentaryCount(a1: Int): String = t.fmt("tool_log_commentary_count", a1)
    override fun toolLogDocumentCount(a1: Int): String = t.fmt("tool_log_document_count", a1)
    override fun toolLogGenbookKeyCount(a1: Int, a2: Int): String = t.fmt("tool_log_genbook_key_count", a1, a2)
    override fun toolLogLabelCount(a1: Int): String = t.fmt("tool_log_label_count", a1)
    override fun toolLogMyDocumentCount(a1: Int): String = t.fmt("tool_log_my_document_count", a1)
    override fun toolLogMyDocumentPageCount(a1: Int): String = t.fmt("tool_log_my_document_page_count", a1)
    override fun toolLogSearchResults(a1: Int, a2: Int): String = t.fmt("tool_log_search_results", a1, a2)
    override fun toolLogStudyPadCount(a1: Int): String = t.fmt("tool_log_study_pad_count", a1)
    override val toolLogWindowCopy: String get() = t.text("tool_log_window_copy")
    override fun toolLogWindowCount(a1: Int): String = t.fmt("tool_log_window_count", a1)
    override val toolLogWindowMinimized: String get() = t.text("tool_log_window_minimized")
    override val toolLogWindowRemoved: String get() = t.text("tool_log_window_removed")

    override fun toolDisplayName(tool: AgentTool): String {
        val resource = when (tool) {
            AgentTool.ADD_BOOKMARK_NOTE -> "tool_add_bookmark_note"
            AgentTool.ADD_LABEL_TO_BOOKMARK -> "tool_add_label_to_bookmark"
            AgentTool.ADD_MY_DOCUMENT_PAGE -> "tool_add_my_document_page"
            AgentTool.ADD_STUDY_PAD_ENTRY -> "tool_add_study_pad_entry"
            AgentTool.CREATE_BOOKMARK -> "tool_create_bookmark"
            AgentTool.CREATE_LABEL -> "tool_create_label"
            AgentTool.CREATE_MY_DOCUMENT -> "tool_create_my_document"
            AgentTool.CREATE_STUDY_PAD -> "tool_create_study_pad"
            AgentTool.CREATE_WINDOW -> "tool_create_window"
            AgentTool.DELETE_BOOKMARK -> "tool_delete_bookmark"
            AgentTool.DELETE_LABEL -> "tool_delete_label"
            AgentTool.DELETE_MY_DOCUMENT_PAGE -> "tool_delete_my_document_page"
            AgentTool.EDIT_MY_DOCUMENT_PAGE -> "tool_edit_my_document_page"
            AgentTool.FINISH_WITHOUT_DOCUMENT -> "tool_finish_without_document"
            AgentTool.FINISH_WITH_MY_DOCUMENT_PAGE -> "tool_finish_with_my_document_page"
            AgentTool.FINISH_WITH_STUDY_PAD -> "tool_finish_with_study_pad"
            AgentTool.GET_ALL_LABELS -> "tool_get_all_labels"
            AgentTool.GET_BOOKMARKS_FOR_VERSE -> "tool_get_bookmarks_for_verse"
            AgentTool.GET_BOOKMARKS_WITH_LABEL -> "tool_get_bookmarks_with_label"
            AgentTool.GET_COMMENTARIES -> "tool_get_commentaries"
            AgentTool.GET_DICTIONARY_ENTRY -> "tool_get_dictionary_entry"
            AgentTool.GET_GENBOOK_CONTENT -> "tool_get_genbook_content"
            AgentTool.GET_GENBOOK_KEYS -> "tool_get_genbook_keys"
            AgentTool.GET_INSTALLED_DOCUMENTS -> "tool_get_installed_documents"
            AgentTool.GET_MY_DOCUMENTS -> "tool_get_my_documents"
            AgentTool.GET_MY_DOCUMENT_PAGES -> "tool_get_my_document_pages"
            AgentTool.GET_STUDY_PAD_CONTENT -> "tool_get_study_pad_content"
            AgentTool.GET_VERSE_CONTENT -> "tool_get_verse_content"
            AgentTool.GET_WINDOWS -> "tool_get_windows"
            AgentTool.MANAGE_WINDOW -> "tool_manage_window"
            AgentTool.REMOVE_LABEL_FROM_BOOKMARK -> "tool_remove_label_from_bookmark"
            AgentTool.SEARCH_BIBLE -> "tool_search_bible"
            AgentTool.SEARCH_BY_STRONGS -> "tool_search_by_strongs"
            AgentTool.SEARCH_STUDY_PADS -> "tool_search_study_pads"
            AgentTool.SET_DOCUMENT_TITLE -> "tool_set_document_title"
            AgentTool.SET_WINDOW_DOCUMENT -> "tool_set_window_document"
            AgentTool.UPDATE_BOOKMARK_NOTE -> "tool_update_bookmark_note"
            AgentTool.UPDATE_STUDYPAD_TEXT_ENTRY -> "tool_update_studypad_text_entry"
        }
        return t.text(resource)
    }

    override val defaultAgentSystemPrompt: String
        get() = t.raw("llm_agent_system_prompt")
    override val defaultTextTransformationSystemPrompt: String
        get() = t.raw("llm_text_transformation_system_prompt")
}
