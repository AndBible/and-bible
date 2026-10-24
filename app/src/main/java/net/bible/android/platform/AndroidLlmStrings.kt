package net.bible.android.platform

import android.content.Context
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import net.bible.service.llm.LlmStrings

/** Reads each resource at access time, including after a locale change. */
class AndroidLlmStrings(private val context: Context) : LlmStrings {
    override val actionActiveWindow: String get() = context.getString(R.string.action_active_window)
    override fun actionAddEntryToStudypad(a1: String): String = context.getString(R.string.action_add_entry_to_studypad, a1)
    override fun actionAddLabelToBookmark(a1: String, a2: String): String = context.getString(R.string.action_add_label_to_bookmark, a1, a2)
    override fun actionAddMyDocumentPage(a1: String, a2: String): String = context.getString(R.string.action_add_my_document_page, a1, a2)
    override fun actionAddNoteToBookmarkAt(a1: String): String = context.getString(R.string.action_add_note_to_bookmark_at, a1)
    override fun actionCloseWindow(a1: String): String = context.getString(R.string.action_close_window, a1)
    override fun actionCreateBookmarkAt(a1: String): String = context.getString(R.string.action_create_bookmark_at, a1)
    override fun actionCreateBookmarkAtWithExtras(a1: String, a2: String): String = context.getString(R.string.action_create_bookmark_at_with_extras, a1, a2)
    override fun actionCreateLabel(a1: String): String = context.getString(R.string.action_create_label, a1)
    override fun actionCreateMyDocument(a1: String): String = context.getString(R.string.action_create_my_document, a1)
    override fun actionCreateStudypad(a1: String, a2: Int): String = context.getString(R.string.action_create_studypad, a1, a2)
    override val actionCreateWindow: String get() = context.getString(R.string.action_create_window)
    override fun actionCreateWindowWithDoc(a1: String): String = context.getString(R.string.action_create_window_with_doc, a1)
    override fun actionCreateWindowWithDocAndKey(a1: String, a2: String): String = context.getString(R.string.action_create_window_with_doc_and_key, a1, a2)
    override fun actionDeleteBookmark(a1: String): String = context.getString(R.string.action_delete_bookmark, a1)
    override fun actionDeleteLabel(a1: String): String = context.getString(R.string.action_delete_label, a1)
    override fun actionDeleteMyDocumentPage(a1: String): String = context.getString(R.string.action_delete_my_document_page, a1)
    override fun actionEditMyDocumentPage(a1: String): String = context.getString(R.string.action_edit_my_document_page, a1)
    override fun actionFinish(a1: String): String = context.getString(R.string.action_finish, a1)
    override fun actionLabels(a1: String): String = context.getString(R.string.action_labels, a1)
    override fun actionMinimizeWindow(a1: String): String = context.getString(R.string.action_minimize_window, a1)
    override fun actionOpenMyDocumentPage(a1: String): String = context.getString(R.string.action_open_my_document_page, a1)
    override fun actionOpenStudypad(a1: String): String = context.getString(R.string.action_open_studypad, a1)
    override fun actionRemoveLabelFromBookmark(a1: String, a2: String): String = context.getString(R.string.action_remove_label_from_bookmark, a1, a2)
    override fun actionRestoreWindow(a1: String): String = context.getString(R.string.action_restore_window, a1)
    override fun actionSetDocumentTitle(a1: String): String = context.getString(R.string.action_set_document_title, a1)
    override fun actionSetWindowDocument(a1: String, a2: String): String = context.getString(R.string.action_set_window_document, a1, a2)
    override fun actionSetWindowDocumentWithKey(a1: String, a2: String, a3: String): String = context.getString(R.string.action_set_window_document_with_key, a1, a2, a3)
    override fun actionUpdateNoteOnBookmarkAt(a1: String): String = context.getString(R.string.action_update_note_on_bookmark_at, a1)
    override fun actionUpdateStudypadTextEntry(a1: String): String = context.getString(R.string.action_update_studypad_text_entry, a1)
    override val actionWithNote: String get() = context.getString(R.string.action_with_note)
    override val agentAlreadyRunning: String get() = context.getString(R.string.agent_already_running)
    override val agentLogCancelled: String get() = context.getString(R.string.agent_log_cancelled)
    override val agentLogCompleted: String get() = context.getString(R.string.agent_log_completed)
    override fun agentLogDone(a1: String): String = context.getString(R.string.agent_log_done, a1)
    override fun agentLogExecuting(a1: String): String = context.getString(R.string.agent_log_executing, a1)
    override fun agentLogIteration(a1: Int): String = context.getString(R.string.agent_log_iteration, a1)
    override val agentLogResponseReceived: String get() = context.getString(R.string.agent_log_response_received)
    override fun agentLogSaved(a1: String): String = context.getString(R.string.agent_log_saved, a1)
    override fun agentLogTool(a1: String): String = context.getString(R.string.agent_log_tool, a1)
    override fun agentLogToolFailed(a1: String): String = context.getString(R.string.agent_log_tool_failed, a1)
    override val agentLogViewRaw: String get() = context.getString(R.string.agent_log_view_raw)
    override val aiAgentNotificationCancel: String get() = context.getString(R.string.ai_agent_notification_cancel)
    override val aiAgentNotificationCompleted: String get() = context.getString(R.string.ai_agent_notification_completed)
    override val aiAgentNotificationCompletedTap: String get() = context.getString(R.string.ai_agent_notification_completed_tap)
    override val aiAgentNotificationError: String get() = context.getString(R.string.ai_agent_notification_error)
    override val aiAgentNotificationPermissionNeeded: String get() = context.getString(R.string.ai_agent_notification_permission_needed)
    override val aiAgentNotificationPermissionTap: String get() = context.getString(R.string.ai_agent_notification_permission_tap)
    override val aiAgentNotificationRunning: String get() = context.getString(R.string.ai_agent_notification_running)
    override fun aiLanguageAppDefault(a1: String): String = context.getString(R.string.ai_language_app_default, a1)
    override val aiRegeneratePromptNotFound: String get() = context.getString(R.string.ai_regenerate_prompt_not_found)
    override val cancel: String get() = context.getString(R.string.cancel)
    override fun commentaryFilterDialogMessage(a1: String): String = context.getString(R.string.commentary_filter_dialog_message, a1)
    override val commentaryFilterDialogTitle: String get() = context.getString(R.string.commentary_filter_dialog_title)
    override fun commentaryFilterItem(a1: String, a2: String, a3: String): String = context.getString(R.string.commentary_filter_item, a1, a2, a3)
    override fun commentaryFilterTotalTokens(a1: String, a2: String): String = context.getString(R.string.commentary_filter_total_tokens, a1, a2)
    override val defaultPromptAskQuestion: String get() = context.getString(R.string.default_prompt_ask_question)
    override val defaultPromptAskQuestionDesc: String get() = context.getString(R.string.default_prompt_ask_question_desc)
    override val defaultPromptBookmarkAnnotate: String get() = context.getString(R.string.default_prompt_bookmark_annotate)
    override val defaultPromptBookmarkAnnotateDesc: String get() = context.getString(R.string.default_prompt_bookmark_annotate_desc)
    override val defaultPromptCompareTranslations: String get() = context.getString(R.string.default_prompt_compare_translations)
    override val defaultPromptCompareTranslationsDesc: String get() = context.getString(R.string.default_prompt_compare_translations_desc)
    override val defaultPromptCrossReferences: String get() = context.getString(R.string.default_prompt_cross_references)
    override val defaultPromptCrossReferencesDesc: String get() = context.getString(R.string.default_prompt_cross_references_desc)
    override val defaultPromptCustom: String get() = context.getString(R.string.default_prompt_custom)
    override val defaultPromptCustomDesc: String get() = context.getString(R.string.default_prompt_custom_desc)
    override val defaultPromptCustomTemplate: String get() = context.getString(R.string.default_prompt_custom_template)
    override val defaultPromptEnhanceNote: String get() = context.getString(R.string.default_prompt_enhance_note)
    override val defaultPromptEnhanceNoteDesc: String get() = context.getString(R.string.default_prompt_enhance_note_desc)
    override val defaultPromptExplainVerses: String get() = context.getString(R.string.default_prompt_explain_verses)
    override val defaultPromptExplainVersesDesc: String get() = context.getString(R.string.default_prompt_explain_verses_desc)
    override val defaultPromptExplainVersesStudypad: String get() = context.getString(R.string.default_prompt_explain_verses_studypad)
    override val defaultPromptExplainVersesStudypadDesc: String get() = context.getString(R.string.default_prompt_explain_verses_studypad_desc)
    override val defaultPromptStudyLayout: String get() = context.getString(R.string.default_prompt_study_layout)
    override val defaultPromptStudyLayoutDesc: String get() = context.getString(R.string.default_prompt_study_layout_desc)
    override val defaultPromptSummary: String get() = context.getString(R.string.default_prompt_summary)
    override val defaultPromptSummaryDesc: String get() = context.getString(R.string.default_prompt_summary_desc)
    override val defaultPromptThematicStudy: String get() = context.getString(R.string.default_prompt_thematic_study)
    override val defaultPromptThematicStudyDesc: String get() = context.getString(R.string.default_prompt_thematic_study_desc)
    override fun defaultPromptTranslateToLanguage(a1: String): String = context.getString(R.string.default_prompt_translate_to_language, a1)
    override val defaultPromptTranslateToUiLanguageDesc: String get() = context.getString(R.string.default_prompt_translate_to_ui_language_desc)
    override val defaultPromptWordStudy: String get() = context.getString(R.string.default_prompt_word_study)
    override val defaultPromptWordStudyDesc: String get() = context.getString(R.string.default_prompt_word_study_desc)
    override val defaultPromptWorkspaceAssistant: String get() = context.getString(R.string.default_prompt_workspace_assistant)
    override val defaultPromptWorkspaceAssistantDesc: String get() = context.getString(R.string.default_prompt_workspace_assistant_desc)
    override val easySetupAnthropicDesc: String get() = context.getString(R.string.easy_setup_anthropic_desc)
    override val easySetupFreeTier: String get() = context.getString(R.string.easy_setup_free_tier)
    override val easySetupGeminiDesc: String get() = context.getString(R.string.easy_setup_gemini_desc)
    override val easySetupOpenaiDesc: String get() = context.getString(R.string.easy_setup_openai_desc)
    override fun llmContinueIterationsMessage(a1: Int, a2: Int): String = context.getString(R.string.llm_continue_iterations_message, a1, a2)
    override val llmContinueIterationsTitle: String get() = context.getString(R.string.llm_continue_iterations_title)
    override fun llmCostTotal(a1: String): String = context.getString(R.string.llm_cost_total, a1)
    override val llmDefaultDocumentTitle: String get() = context.getString(R.string.llm_default_document_title)
    override val llmDefaultStudypadOpened: String get() = context.getString(R.string.llm_default_studypad_opened)
    override val llmDefaultTaskCompleted: String get() = context.getString(R.string.llm_default_task_completed)
    override fun llmErrorMaxIterations(a1: Int): String = context.getString(R.string.llm_error_max_iterations, a1)
    override val llmErrorUnknown: String get() = context.getString(R.string.llm_error_unknown)
    override val llmNoDocumentCreationIntercepted: String get() = context.getString(R.string.llm_no_document_creation_intercepted)
    override val llmNoteUpdated: String get() = context.getString(R.string.llm_note_updated)
    override fun llmParseError(a1: String): String = context.getString(R.string.llm_parse_error, a1)
    override fun llmParseErrorAnthropic(a1: String): String = context.getString(R.string.llm_parse_error_anthropic, a1)
    override val llmParseErrorNoChoices: String get() = context.getString(R.string.llm_parse_error_no_choices)
    override fun llmParseErrorOpenai(a1: String): String = context.getString(R.string.llm_parse_error_openai, a1)
    override val llmProviderCustom: String get() = context.getString(R.string.llm_provider_custom)
    override fun llmRateLimitedRetrying(a1: String): String = context.getString(R.string.llm_rate_limited_retrying, a1)
    override val llmUsageSummaryDefault: String get() = context.getString(R.string.llm_usage_summary_default)
    override fun llmUsageSummaryFormat(a1: Long, a2: Long, a3: String): String = context.getString(R.string.llm_usage_summary_format, a1, a2, a3)
    override val okay: String get() = context.getString(R.string.okay)
    override fun permissionAlwaysAllowConfirm(a1: String): String = context.getString(R.string.permission_always_allow_confirm, a1)
    override val permissionAlwaysAllowConfirmResetHint: String get() = context.getString(R.string.permission_always_allow_confirm_reset_hint)
    override val permissionAlwaysAllowConfirmTitle: String get() = context.getString(R.string.permission_always_allow_confirm_title)
    override val promptCategoryFavorites: String get() = context.getString(R.string.prompt_category_favorites)
    override val promptCategoryGeneral: String get() = context.getString(R.string.prompt_category_general)
    override val promptCategoryNotes: String get() = context.getString(R.string.prompt_category_notes)
    override val promptCategoryStudy: String get() = context.getString(R.string.prompt_category_study)
    override val promptCategoryTest: String get() = context.getString(R.string.prompt_category_test)
    override val promptCategoryUncategorized: String get() = context.getString(R.string.prompt_category_uncategorized)
    override val promptModelDefault: String get() = context.getString(R.string.prompt_model_default)
    override fun rawLlmLogEntryApiResponse(a1: Int): String = context.getString(R.string.raw_llm_log_entry_api_response, a1)
    override val rawLlmLogEntryAssistant: String get() = context.getString(R.string.raw_llm_log_entry_assistant)
    override val rawLlmLogEntryEmpty: String get() = context.getString(R.string.raw_llm_log_entry_empty)
    override val rawLlmLogEntrySystem: String get() = context.getString(R.string.raw_llm_log_entry_system)
    override fun rawLlmLogEntryTokens(a1: String): String = context.getString(R.string.raw_llm_log_entry_tokens, a1)
    override fun rawLlmLogEntryToolCall(a1: String): String = context.getString(R.string.raw_llm_log_entry_tool_call, a1)
    override fun rawLlmLogEntryToolDefinitions(a1: Int): String = context.getString(R.string.raw_llm_log_entry_tool_definitions, a1)
    override fun rawLlmLogEntryToolDesc(a1: String): String = context.getString(R.string.raw_llm_log_entry_tool_desc, a1)
    override fun rawLlmLogEntryToolParams(a1: String): String = context.getString(R.string.raw_llm_log_entry_tool_params, a1)
    override fun rawLlmLogEntryToolResult(a1: String): String = context.getString(R.string.raw_llm_log_entry_tool_result, a1)
    override fun rawLlmLogEntryUsage(a1: String, a2: String, a3: String): String = context.getString(R.string.raw_llm_log_entry_usage, a1, a2, a3)
    override val rawLlmLogEntryUser: String get() = context.getString(R.string.raw_llm_log_entry_user)
    override fun rawLlmLogTotal(a1: String, a2: String, a3: String): String = context.getString(R.string.raw_llm_log_total, a1, a2, a3)
    override fun rawLogItemTokens(a1: String, a2: String): String = context.getString(R.string.raw_log_item_tokens, a1, a2)
    override val selectAll: String get() = context.getString(R.string.select_all)
    override val selectNone: String get() = context.getString(R.string.select_none)
    override val toolCategoryBibleSearch: String get() = context.getString(R.string.tool_category_bible_search)
    override val toolCategoryBookmarks: String get() = context.getString(R.string.tool_category_bookmarks)
    override val toolCategoryGeneralBooks: String get() = context.getString(R.string.tool_category_general_books)
    override val toolCategoryLabels: String get() = context.getString(R.string.tool_category_labels)
    override val toolCategoryMyDocuments: String get() = context.getString(R.string.tool_category_my_documents)
    override val toolCategoryStudyPads: String get() = context.getString(R.string.tool_category_study_pads)
    override val toolCategoryWindows: String get() = context.getString(R.string.tool_category_windows)
    override fun toolLogBookmarkCount(a1: Int): String = context.getString(R.string.tool_log_bookmark_count, a1)
    override fun toolLogCommentaryCount(a1: Int): String = context.getString(R.string.tool_log_commentary_count, a1)
    override fun toolLogDocumentCount(a1: Int): String = context.getString(R.string.tool_log_document_count, a1)
    override fun toolLogGenbookKeyCount(a1: Int, a2: Int): String = context.getString(R.string.tool_log_genbook_key_count, a1, a2)
    override fun toolLogLabelCount(a1: Int): String = context.getString(R.string.tool_log_label_count, a1)
    override fun toolLogMyDocumentCount(a1: Int): String = context.getString(R.string.tool_log_my_document_count, a1)
    override fun toolLogMyDocumentPageCount(a1: Int): String = context.getString(R.string.tool_log_my_document_page_count, a1)
    override fun toolLogSearchResults(a1: Int, a2: Int): String = context.getString(R.string.tool_log_search_results, a1, a2)
    override fun toolLogStudyPadCount(a1: Int): String = context.getString(R.string.tool_log_study_pad_count, a1)
    override val toolLogWindowCopy: String get() = context.getString(R.string.tool_log_window_copy)
    override fun toolLogWindowCount(a1: Int): String = context.getString(R.string.tool_log_window_count, a1)
    override val toolLogWindowMinimized: String get() = context.getString(R.string.tool_log_window_minimized)
    override val toolLogWindowRemoved: String get() = context.getString(R.string.tool_log_window_removed)

    override fun toolDisplayName(tool: AgentTool): String {
        val resource = when (tool) {
            AgentTool.ADD_BOOKMARK_NOTE -> R.string.tool_add_bookmark_note
            AgentTool.ADD_LABEL_TO_BOOKMARK -> R.string.tool_add_label_to_bookmark
            AgentTool.ADD_MY_DOCUMENT_PAGE -> R.string.tool_add_my_document_page
            AgentTool.ADD_STUDY_PAD_ENTRY -> R.string.tool_add_study_pad_entry
            AgentTool.CREATE_BOOKMARK -> R.string.tool_create_bookmark
            AgentTool.CREATE_LABEL -> R.string.tool_create_label
            AgentTool.CREATE_MY_DOCUMENT -> R.string.tool_create_my_document
            AgentTool.CREATE_STUDY_PAD -> R.string.tool_create_study_pad
            AgentTool.CREATE_WINDOW -> R.string.tool_create_window
            AgentTool.DELETE_BOOKMARK -> R.string.tool_delete_bookmark
            AgentTool.DELETE_LABEL -> R.string.tool_delete_label
            AgentTool.DELETE_MY_DOCUMENT_PAGE -> R.string.tool_delete_my_document_page
            AgentTool.EDIT_MY_DOCUMENT_PAGE -> R.string.tool_edit_my_document_page
            AgentTool.FINISH_WITHOUT_DOCUMENT -> R.string.tool_finish_without_document
            AgentTool.FINISH_WITH_MY_DOCUMENT_PAGE -> R.string.tool_finish_with_my_document_page
            AgentTool.FINISH_WITH_STUDY_PAD -> R.string.tool_finish_with_study_pad
            AgentTool.GET_ALL_LABELS -> R.string.tool_get_all_labels
            AgentTool.GET_BOOKMARKS_FOR_VERSE -> R.string.tool_get_bookmarks_for_verse
            AgentTool.GET_BOOKMARKS_WITH_LABEL -> R.string.tool_get_bookmarks_with_label
            AgentTool.GET_COMMENTARIES -> R.string.tool_get_commentaries
            AgentTool.GET_DICTIONARY_ENTRY -> R.string.tool_get_dictionary_entry
            AgentTool.GET_GENBOOK_CONTENT -> R.string.tool_get_genbook_content
            AgentTool.GET_GENBOOK_KEYS -> R.string.tool_get_genbook_keys
            AgentTool.GET_INSTALLED_DOCUMENTS -> R.string.tool_get_installed_documents
            AgentTool.GET_MY_DOCUMENTS -> R.string.tool_get_my_documents
            AgentTool.GET_MY_DOCUMENT_PAGES -> R.string.tool_get_my_document_pages
            AgentTool.GET_STUDY_PAD_CONTENT -> R.string.tool_get_study_pad_content
            AgentTool.GET_VERSE_CONTENT -> R.string.tool_get_verse_content
            AgentTool.GET_WINDOWS -> R.string.tool_get_windows
            AgentTool.MANAGE_WINDOW -> R.string.tool_manage_window
            AgentTool.REMOVE_LABEL_FROM_BOOKMARK -> R.string.tool_remove_label_from_bookmark
            AgentTool.SEARCH_BIBLE -> R.string.tool_search_bible
            AgentTool.SEARCH_BY_STRONGS -> R.string.tool_search_by_strongs
            AgentTool.SEARCH_STUDY_PADS -> R.string.tool_search_study_pads
            AgentTool.SET_DOCUMENT_TITLE -> R.string.tool_set_document_title
            AgentTool.SET_WINDOW_DOCUMENT -> R.string.tool_set_window_document
            AgentTool.UPDATE_BOOKMARK_NOTE -> R.string.tool_update_bookmark_note
            AgentTool.UPDATE_STUDYPAD_TEXT_ENTRY -> R.string.tool_update_studypad_text_entry
        }
        return context.getString(resource)
    }

    override val defaultAgentSystemPrompt: String
        get() = context.resources.openRawResource(R.raw.llm_agent_system_prompt).bufferedReader().use { it.readText() }
    override val defaultTextTransformationSystemPrompt: String
        get() = context.resources.openRawResource(R.raw.llm_text_transformation_system_prompt).bufferedReader().use { it.readText() }
}
