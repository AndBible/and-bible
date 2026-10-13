package net.bible.android.platform

import android.content.Context
import org.koin.android.ext.koin.androidContext
import androidx.test.core.app.ApplicationProvider
import kotlin.reflect.KParameter
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties
import net.bible.android.TestBibleApplication
import net.bible.android.activity.R
import net.bible.service.llm.AgentTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = TestBibleApplication::class)
class AndroidLlmStringsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    // Resolve by name so the missing implementation is a runtime RED, not a compilation error.
    private fun strings(): Any = Class.forName("net.bible.android.platform.AndroidLlmStrings")
        .getConstructor(Context::class.java).newInstance(context)
    private fun port() = Class.forName("net.bible.service.llm.LlmStrings").kotlin

    @Test fun everyTypedMemberCanBeCalledWithItsDeclaredArguments() {
        val strings = strings()
        val port = port()
        // Literal BASE resource inventory catches missing members and incorrect resource mappings.
        val expectedResources = mapOf(
            "actionActiveWindow" to R.string.action_active_window,
            "actionAddEntryToStudypad" to R.string.action_add_entry_to_studypad,
            "actionAddLabelToBookmark" to R.string.action_add_label_to_bookmark,
            "actionAddMyDocumentPage" to R.string.action_add_my_document_page,
            "actionAddNoteToBookmarkAt" to R.string.action_add_note_to_bookmark_at,
            "actionCloseWindow" to R.string.action_close_window,
            "actionCreateBookmarkAt" to R.string.action_create_bookmark_at,
            "actionCreateBookmarkAtWithExtras" to R.string.action_create_bookmark_at_with_extras,
            "actionCreateLabel" to R.string.action_create_label,
            "actionCreateMyDocument" to R.string.action_create_my_document,
            "actionCreateStudypad" to R.string.action_create_studypad,
            "actionCreateWindow" to R.string.action_create_window,
            "actionCreateWindowWithDoc" to R.string.action_create_window_with_doc,
            "actionCreateWindowWithDocAndKey" to R.string.action_create_window_with_doc_and_key,
            "actionDeleteBookmark" to R.string.action_delete_bookmark,
            "actionDeleteLabel" to R.string.action_delete_label,
            "actionDeleteMyDocumentPage" to R.string.action_delete_my_document_page,
            "actionEditMyDocumentPage" to R.string.action_edit_my_document_page,
            "actionFinish" to R.string.action_finish,
            "actionLabels" to R.string.action_labels,
            "actionMinimizeWindow" to R.string.action_minimize_window,
            "actionOpenMyDocumentPage" to R.string.action_open_my_document_page,
            "actionOpenStudypad" to R.string.action_open_studypad,
            "actionRemoveLabelFromBookmark" to R.string.action_remove_label_from_bookmark,
            "actionRestoreWindow" to R.string.action_restore_window,
            "actionSetDocumentTitle" to R.string.action_set_document_title,
            "actionSetWindowDocument" to R.string.action_set_window_document,
            "actionSetWindowDocumentWithKey" to R.string.action_set_window_document_with_key,
            "actionUpdateNoteOnBookmarkAt" to R.string.action_update_note_on_bookmark_at,
            "actionUpdateStudypadTextEntry" to R.string.action_update_studypad_text_entry,
            "actionWithNote" to R.string.action_with_note,
            "agentAlreadyRunning" to R.string.agent_already_running,
            "agentLogCancelled" to R.string.agent_log_cancelled,
            "agentLogCompleted" to R.string.agent_log_completed,
            "agentLogDone" to R.string.agent_log_done,
            "agentLogExecuting" to R.string.agent_log_executing,
            "agentLogIteration" to R.string.agent_log_iteration,
            "agentLogResponseReceived" to R.string.agent_log_response_received,
            "agentLogSaved" to R.string.agent_log_saved,
            "agentLogTool" to R.string.agent_log_tool,
            "agentLogToolFailed" to R.string.agent_log_tool_failed,
            "agentLogViewRaw" to R.string.agent_log_view_raw,
            "aiAgentNotificationCancel" to R.string.ai_agent_notification_cancel,
            "aiAgentNotificationCompleted" to R.string.ai_agent_notification_completed,
            "aiAgentNotificationCompletedTap" to R.string.ai_agent_notification_completed_tap,
            "aiAgentNotificationError" to R.string.ai_agent_notification_error,
            "aiAgentNotificationPermissionNeeded" to R.string.ai_agent_notification_permission_needed,
            "aiAgentNotificationPermissionTap" to R.string.ai_agent_notification_permission_tap,
            "aiAgentNotificationRunning" to R.string.ai_agent_notification_running,
            "aiLanguageAppDefault" to R.string.ai_language_app_default,
            "aiRegeneratePromptNotFound" to R.string.ai_regenerate_prompt_not_found,
            "cancel" to R.string.cancel,
            "commentaryFilterDialogMessage" to R.string.commentary_filter_dialog_message,
            "commentaryFilterDialogTitle" to R.string.commentary_filter_dialog_title,
            "commentaryFilterItem" to R.string.commentary_filter_item,
            "commentaryFilterTotalTokens" to R.string.commentary_filter_total_tokens,
            "defaultPromptAskQuestion" to R.string.default_prompt_ask_question,
            "defaultPromptAskQuestionDesc" to R.string.default_prompt_ask_question_desc,
            "defaultPromptBookmarkAnnotate" to R.string.default_prompt_bookmark_annotate,
            "defaultPromptBookmarkAnnotateDesc" to R.string.default_prompt_bookmark_annotate_desc,
            "defaultPromptCompareTranslations" to R.string.default_prompt_compare_translations,
            "defaultPromptCompareTranslationsDesc" to R.string.default_prompt_compare_translations_desc,
            "defaultPromptCrossReferences" to R.string.default_prompt_cross_references,
            "defaultPromptCrossReferencesDesc" to R.string.default_prompt_cross_references_desc,
            "defaultPromptCustom" to R.string.default_prompt_custom,
            "defaultPromptCustomDesc" to R.string.default_prompt_custom_desc,
            "defaultPromptCustomTemplate" to R.string.default_prompt_custom_template,
            "defaultPromptEnhanceNote" to R.string.default_prompt_enhance_note,
            "defaultPromptEnhanceNoteDesc" to R.string.default_prompt_enhance_note_desc,
            "defaultPromptExplainVerses" to R.string.default_prompt_explain_verses,
            "defaultPromptExplainVersesDesc" to R.string.default_prompt_explain_verses_desc,
            "defaultPromptExplainVersesStudypad" to R.string.default_prompt_explain_verses_studypad,
            "defaultPromptExplainVersesStudypadDesc" to R.string.default_prompt_explain_verses_studypad_desc,
            "defaultPromptStudyLayout" to R.string.default_prompt_study_layout,
            "defaultPromptStudyLayoutDesc" to R.string.default_prompt_study_layout_desc,
            "defaultPromptSummary" to R.string.default_prompt_summary,
            "defaultPromptSummaryDesc" to R.string.default_prompt_summary_desc,
            "defaultPromptThematicStudy" to R.string.default_prompt_thematic_study,
            "defaultPromptThematicStudyDesc" to R.string.default_prompt_thematic_study_desc,
            "defaultPromptTranslateToLanguage" to R.string.default_prompt_translate_to_language,
            "defaultPromptTranslateToUiLanguageDesc" to R.string.default_prompt_translate_to_ui_language_desc,
            "defaultPromptWordStudy" to R.string.default_prompt_word_study,
            "defaultPromptWordStudyDesc" to R.string.default_prompt_word_study_desc,
            "defaultPromptWorkspaceAssistant" to R.string.default_prompt_workspace_assistant,
            "defaultPromptWorkspaceAssistantDesc" to R.string.default_prompt_workspace_assistant_desc,
            "easySetupAnthropicDesc" to R.string.easy_setup_anthropic_desc,
            "easySetupFreeTier" to R.string.easy_setup_free_tier,
            "easySetupGeminiDesc" to R.string.easy_setup_gemini_desc,
            "easySetupOpenaiDesc" to R.string.easy_setup_openai_desc,
            "llmContinueIterationsMessage" to R.string.llm_continue_iterations_message,
            "llmContinueIterationsTitle" to R.string.llm_continue_iterations_title,
            "llmCostTotal" to R.string.llm_cost_total,
            "llmDefaultDocumentTitle" to R.string.llm_default_document_title,
            "llmDefaultStudypadOpened" to R.string.llm_default_studypad_opened,
            "llmDefaultTaskCompleted" to R.string.llm_default_task_completed,
            "llmErrorMaxIterations" to R.string.llm_error_max_iterations,
            "llmErrorUnknown" to R.string.llm_error_unknown,
            "llmNoDocumentCreationIntercepted" to R.string.llm_no_document_creation_intercepted,
            "llmNoteUpdated" to R.string.llm_note_updated,
            "llmParseError" to R.string.llm_parse_error,
            "llmParseErrorAnthropic" to R.string.llm_parse_error_anthropic,
            "llmParseErrorNoChoices" to R.string.llm_parse_error_no_choices,
            "llmParseErrorOpenai" to R.string.llm_parse_error_openai,
            "llmProviderCustom" to R.string.llm_provider_custom,
            "llmRateLimitedRetrying" to R.string.llm_rate_limited_retrying,
            "llmUsageSummaryDefault" to R.string.llm_usage_summary_default,
            "llmUsageSummaryFormat" to R.string.llm_usage_summary_format,
            "okay" to R.string.okay,
            "permissionAlwaysAllowConfirm" to R.string.permission_always_allow_confirm,
            "permissionAlwaysAllowConfirmResetHint" to R.string.permission_always_allow_confirm_reset_hint,
            "permissionAlwaysAllowConfirmTitle" to R.string.permission_always_allow_confirm_title,
            "promptCategoryFavorites" to R.string.prompt_category_favorites,
            "promptCategoryGeneral" to R.string.prompt_category_general,
            "promptCategoryNotes" to R.string.prompt_category_notes,
            "promptCategoryStudy" to R.string.prompt_category_study,
            "promptCategoryTest" to R.string.prompt_category_test,
            "promptCategoryUncategorized" to R.string.prompt_category_uncategorized,
            "promptModelDefault" to R.string.prompt_model_default,
            "rawLlmLogEntryApiResponse" to R.string.raw_llm_log_entry_api_response,
            "rawLlmLogEntryAssistant" to R.string.raw_llm_log_entry_assistant,
            "rawLlmLogEntryEmpty" to R.string.raw_llm_log_entry_empty,
            "rawLlmLogEntrySystem" to R.string.raw_llm_log_entry_system,
            "rawLlmLogEntryTokens" to R.string.raw_llm_log_entry_tokens,
            "rawLlmLogEntryToolCall" to R.string.raw_llm_log_entry_tool_call,
            "rawLlmLogEntryToolDefinitions" to R.string.raw_llm_log_entry_tool_definitions,
            "rawLlmLogEntryToolDesc" to R.string.raw_llm_log_entry_tool_desc,
            "rawLlmLogEntryToolParams" to R.string.raw_llm_log_entry_tool_params,
            "rawLlmLogEntryToolResult" to R.string.raw_llm_log_entry_tool_result,
            "rawLlmLogEntryUsage" to R.string.raw_llm_log_entry_usage,
            "rawLlmLogEntryUser" to R.string.raw_llm_log_entry_user,
            "rawLlmLogTotal" to R.string.raw_llm_log_total,
            "rawLogItemTokens" to R.string.raw_log_item_tokens,
            "selectAll" to R.string.select_all,
            "selectNone" to R.string.select_none,
            "toolCategoryBibleSearch" to R.string.tool_category_bible_search,
            "toolCategoryBookmarks" to R.string.tool_category_bookmarks,
            "toolCategoryGeneralBooks" to R.string.tool_category_general_books,
            "toolCategoryLabels" to R.string.tool_category_labels,
            "toolCategoryMyDocuments" to R.string.tool_category_my_documents,
            "toolCategoryStudyPads" to R.string.tool_category_study_pads,
            "toolCategoryWindows" to R.string.tool_category_windows,
            "toolLogBookmarkCount" to R.string.tool_log_bookmark_count,
            "toolLogCommentaryCount" to R.string.tool_log_commentary_count,
            "toolLogDocumentCount" to R.string.tool_log_document_count,
            "toolLogGenbookKeyCount" to R.string.tool_log_genbook_key_count,
            "toolLogLabelCount" to R.string.tool_log_label_count,
            "toolLogMyDocumentCount" to R.string.tool_log_my_document_count,
            "toolLogMyDocumentPageCount" to R.string.tool_log_my_document_page_count,
            "toolLogSearchResults" to R.string.tool_log_search_results,
            "toolLogStudyPadCount" to R.string.tool_log_study_pad_count,
            "toolLogWindowCopy" to R.string.tool_log_window_copy,
            "toolLogWindowCount" to R.string.tool_log_window_count,
            "toolLogWindowMinimized" to R.string.tool_log_window_minimized,
            "toolLogWindowRemoved" to R.string.tool_log_window_removed,
        )
        val promptNames = setOf("defaultAgentSystemPrompt", "defaultTextTransformationSystemPrompt")
        assertEquals(expectedResources.keys + promptNames,
            port.declaredMemberProperties.map { it.name }.toSet() +
                port.declaredMemberFunctions.filter { it.name != "toolDisplayName" }.map { it.name }.toSet())
        for (property in port.declaredMemberProperties) {
            val actual = property.getter.call(strings) as String
            assertTrue(property.name, actual.isNotBlank())
            expectedResources[property.name]?.let { assertEquals(property.name, context.getString(it), actual) }
        }
        for (function in port.declaredMemberFunctions.filter { it.name != "toolDisplayName" }) {
            val args = function.parameters.map { parameter ->
                if (parameter.kind == KParameter.Kind.INSTANCE) strings else when (parameter.type.classifier) {
                    String::class -> "x"
                    Int::class -> 1
                    Long::class -> 1L
                    Double::class -> 1.0
                    else -> error("Unexpected type for ${function.name}: ${parameter.type}")
                }
            }
            val actual = function.call(*args.toTypedArray()) as String
            assertTrue(function.name, actual.isNotBlank())
            assertEquals(function.name,
                context.getString(expectedResources.getValue(function.name), *args.drop(1).toTypedArray()), actual)
        }
    }

    @Test fun resourcesAreReadAgainAfterLocaleChanges() {
        val resources = context.resources
        val original = android.content.res.Configuration(resources.configuration)
        val strings = AndroidLlmStrings(context)
        try {
            val english = android.content.res.Configuration(original).apply { setLocale(java.util.Locale.ENGLISH) }
            resources.updateConfiguration(english, resources.displayMetrics)
            val before = strings.cancel
            val finnish = android.content.res.Configuration(original).apply { setLocale(java.util.Locale.forLanguageTag("fi")) }
            resources.updateConfiguration(finnish, resources.displayMetrics)
            assertEquals(context.getString(R.string.cancel), strings.cancel)
            org.junit.Assert.assertNotEquals(before, strings.cancel)
            assertEquals(context.getString(R.string.action_create_label, "x"), strings.actionCreateLabel("x"))
            assertEquals(context.getString(R.string.tool_create_label), strings.toolDisplayName(AgentTool.CREATE_LABEL))
        } finally {
            resources.updateConfiguration(original, resources.displayMetrics)
        }
    }

    @Test fun stringPortResolvesFromTheRealFreshGraph() {
        val isolated = org.koin.dsl.koinApplication {
            androidContext(context)
            modules(net.bible.android.control.buildCoreModule())
        }
        try {
            assertEquals(context.getString(R.string.cancel), isolated.koin.get<net.bible.service.llm.LlmStrings>().cancel)
        } finally {
            isolated.close()
        }
    }

    @Test fun cumulativeUsageKeepsLongCountsWithoutNarrowing() {
        val input = Int.MAX_VALUE.toLong() + 12345L
        val output = Int.MAX_VALUE.toLong() * 3L
        val strings = strings()
        val member = port().declaredMemberFunctions.single { it.name == "llmUsageSummaryFormat" }
        assertEquals(
            context.getString(R.string.llm_usage_summary_format, input, output, "$12.34"),
            member.call(strings, input, output, "$12.34"),
        )
    }

    @Test fun toolDisplayNamesMatchTheOldResourceIds() {
        // Literal pre-port overrides, not derived from the implementation under test.
        val oldDisplayNameResourceIds = mapOf(
            AgentTool.ADD_BOOKMARK_NOTE to R.string.tool_add_bookmark_note,
            AgentTool.ADD_LABEL_TO_BOOKMARK to R.string.tool_add_label_to_bookmark,
            AgentTool.ADD_MY_DOCUMENT_PAGE to R.string.tool_add_my_document_page,
            AgentTool.ADD_STUDY_PAD_ENTRY to R.string.tool_add_study_pad_entry,
            AgentTool.CREATE_BOOKMARK to R.string.tool_create_bookmark,
            AgentTool.CREATE_LABEL to R.string.tool_create_label,
            AgentTool.CREATE_MY_DOCUMENT to R.string.tool_create_my_document,
            AgentTool.CREATE_STUDY_PAD to R.string.tool_create_study_pad,
            AgentTool.CREATE_WINDOW to R.string.tool_create_window,
            AgentTool.DELETE_BOOKMARK to R.string.tool_delete_bookmark,
            AgentTool.DELETE_LABEL to R.string.tool_delete_label,
            AgentTool.DELETE_MY_DOCUMENT_PAGE to R.string.tool_delete_my_document_page,
            AgentTool.EDIT_MY_DOCUMENT_PAGE to R.string.tool_edit_my_document_page,
            AgentTool.FINISH_WITHOUT_DOCUMENT to R.string.tool_finish_without_document,
            AgentTool.FINISH_WITH_MY_DOCUMENT_PAGE to R.string.tool_finish_with_my_document_page,
            AgentTool.FINISH_WITH_STUDY_PAD to R.string.tool_finish_with_study_pad,
            AgentTool.GET_ALL_LABELS to R.string.tool_get_all_labels,
            AgentTool.GET_BOOKMARKS_FOR_VERSE to R.string.tool_get_bookmarks_for_verse,
            AgentTool.GET_BOOKMARKS_WITH_LABEL to R.string.tool_get_bookmarks_with_label,
            AgentTool.GET_COMMENTARIES to R.string.tool_get_commentaries,
            AgentTool.GET_DICTIONARY_ENTRY to R.string.tool_get_dictionary_entry,
            AgentTool.GET_GENBOOK_CONTENT to R.string.tool_get_genbook_content,
            AgentTool.GET_GENBOOK_KEYS to R.string.tool_get_genbook_keys,
            AgentTool.GET_INSTALLED_DOCUMENTS to R.string.tool_get_installed_documents,
            AgentTool.GET_MY_DOCUMENTS to R.string.tool_get_my_documents,
            AgentTool.GET_MY_DOCUMENT_PAGES to R.string.tool_get_my_document_pages,
            AgentTool.GET_STUDY_PAD_CONTENT to R.string.tool_get_study_pad_content,
            AgentTool.GET_VERSE_CONTENT to R.string.tool_get_verse_content,
            AgentTool.GET_WINDOWS to R.string.tool_get_windows,
            AgentTool.MANAGE_WINDOW to R.string.tool_manage_window,
            AgentTool.REMOVE_LABEL_FROM_BOOKMARK to R.string.tool_remove_label_from_bookmark,
            AgentTool.SEARCH_BIBLE to R.string.tool_search_bible,
            AgentTool.SEARCH_BY_STRONGS to R.string.tool_search_by_strongs,
            AgentTool.SEARCH_STUDY_PADS to R.string.tool_search_study_pads,
            AgentTool.SET_DOCUMENT_TITLE to R.string.tool_set_document_title,
            AgentTool.SET_WINDOW_DOCUMENT to R.string.tool_set_window_document,
            AgentTool.UPDATE_BOOKMARK_NOTE to R.string.tool_update_bookmark_note,
            AgentTool.UPDATE_STUDYPAD_TEXT_ENTRY to R.string.tool_update_studypad_text_entry,
        )
        assertEquals(AgentTool.entries.toSet(), oldDisplayNameResourceIds.keys)
        val strings = strings()
        val displayName = port().declaredMemberFunctions.single { it.name == "toolDisplayName" }
        for (tool in AgentTool.entries) {
            val resource = oldDisplayNameResourceIds.getValue(tool)
            val expected = if (resource == 0) tool.camelCaseName else context.getString(resource)
            assertTrue(expected.isNotBlank())
            assertEquals(tool.name, expected, displayName.call(strings, tool))
        }
    }

    @Test fun systemPromptsMatchTheRawResourcesByteForByte() {
        val strings = strings()
        for ((name, resource) in listOf(
            "defaultAgentSystemPrompt" to R.raw.llm_agent_system_prompt,
            "defaultTextTransformationSystemPrompt" to R.raw.llm_text_transformation_system_prompt,
        )) {
            val expected = context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
            assertTrue(expected.isNotBlank())
            assertEquals(expected, port().declaredMemberProperties.single { it.name == name }.getter.call(strings))
        }
    }
}
