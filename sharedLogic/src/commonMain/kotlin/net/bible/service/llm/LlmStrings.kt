package net.bible.service.llm

/** Domain strings and prompt templates. Implementations read resources on every access for locale changes. */
interface LlmStrings {
    val actionActiveWindow: String
    fun actionAddEntryToStudypad(a1: String): String
    fun actionAddLabelToBookmark(a1: String, a2: String): String
    fun actionAddMyDocumentPage(a1: String, a2: String): String
    fun actionAddNoteToBookmarkAt(a1: String): String
    fun actionCloseWindow(a1: String): String
    fun actionCreateBookmarkAt(a1: String): String
    fun actionCreateBookmarkAtWithExtras(a1: String, a2: String): String
    fun actionCreateLabel(a1: String): String
    fun actionCreateMyDocument(a1: String): String
    fun actionCreateStudypad(a1: String, a2: Int): String
    val actionCreateWindow: String
    fun actionCreateWindowWithDoc(a1: String): String
    fun actionCreateWindowWithDocAndKey(a1: String, a2: String): String
    fun actionDeleteBookmark(a1: String): String
    fun actionDeleteLabel(a1: String): String
    fun actionDeleteMyDocumentPage(a1: String): String
    fun actionEditMyDocumentPage(a1: String): String
    fun actionFinish(a1: String): String
    fun actionLabels(a1: String): String
    fun actionMinimizeWindow(a1: String): String
    fun actionOpenMyDocumentPage(a1: String): String
    fun actionOpenStudypad(a1: String): String
    fun actionRemoveLabelFromBookmark(a1: String, a2: String): String
    fun actionRestoreWindow(a1: String): String
    fun actionSetDocumentTitle(a1: String): String
    fun actionSetWindowDocument(a1: String, a2: String): String
    fun actionSetWindowDocumentWithKey(a1: String, a2: String, a3: String): String
    fun actionUpdateNoteOnBookmarkAt(a1: String): String
    fun actionUpdateStudypadTextEntry(a1: String): String
    val actionWithNote: String
    val agentAlreadyRunning: String
    val agentLogCancelled: String
    val agentLogCompleted: String
    fun agentLogDone(a1: String): String
    fun agentLogExecuting(a1: String): String
    fun agentLogIteration(a1: Int): String
    val agentLogResponseReceived: String
    fun agentLogSaved(a1: String): String
    fun agentLogTool(a1: String): String
    fun agentLogToolFailed(a1: String): String
    val agentLogViewRaw: String
    val aiAgentNotificationCancel: String
    val aiAgentNotificationCompleted: String
    val aiAgentNotificationCompletedTap: String
    val aiAgentNotificationError: String
    val aiAgentNotificationPermissionNeeded: String
    val aiAgentNotificationPermissionTap: String
    val aiAgentNotificationRunning: String
    fun aiLanguageAppDefault(a1: String): String
    val aiRegeneratePromptNotFound: String
    val cancel: String
    fun commentaryFilterDialogMessage(a1: String): String
    val commentaryFilterDialogTitle: String
    fun commentaryFilterItem(a1: String, a2: String, a3: String): String
    fun commentaryFilterTotalTokens(a1: String, a2: String): String
    val defaultPromptAskQuestion: String
    val defaultPromptAskQuestionDesc: String
    val defaultPromptBookmarkAnnotate: String
    val defaultPromptBookmarkAnnotateDesc: String
    val defaultPromptCompareTranslations: String
    val defaultPromptCompareTranslationsDesc: String
    val defaultPromptCrossReferences: String
    val defaultPromptCrossReferencesDesc: String
    val defaultPromptCustom: String
    val defaultPromptCustomDesc: String
    val defaultPromptCustomTemplate: String
    val defaultPromptEnhanceNote: String
    val defaultPromptEnhanceNoteDesc: String
    val defaultPromptExplainVerses: String
    val defaultPromptExplainVersesDesc: String
    val defaultPromptExplainVersesStudypad: String
    val defaultPromptExplainVersesStudypadDesc: String
    val defaultPromptStudyLayout: String
    val defaultPromptStudyLayoutDesc: String
    val defaultPromptSummary: String
    val defaultPromptSummaryDesc: String
    val defaultPromptThematicStudy: String
    val defaultPromptThematicStudyDesc: String
    fun defaultPromptTranslateToLanguage(a1: String): String
    val defaultPromptTranslateToUiLanguageDesc: String
    val defaultPromptWordStudy: String
    val defaultPromptWordStudyDesc: String
    val defaultPromptWorkspaceAssistant: String
    val defaultPromptWorkspaceAssistantDesc: String
    val easySetupAnthropicDesc: String
    val easySetupFreeTier: String
    val easySetupGeminiDesc: String
    val easySetupOpenaiDesc: String
    fun llmContinueIterationsMessage(a1: Int, a2: Int): String
    val llmContinueIterationsTitle: String
    fun llmCostTotal(a1: String): String
    val llmDefaultDocumentTitle: String
    val llmDefaultStudypadOpened: String
    val llmDefaultTaskCompleted: String
    fun llmErrorMaxIterations(a1: Int): String
    val llmErrorUnknown: String
    val llmNoDocumentCreationIntercepted: String
    val llmNoteUpdated: String
    fun llmParseError(a1: String): String
    fun llmParseErrorAnthropic(a1: String): String
    val llmParseErrorNoChoices: String
    fun llmParseErrorOpenai(a1: String): String
    val llmProviderCustom: String
    fun llmRateLimitedRetrying(a1: String): String
    val llmUsageSummaryDefault: String
    fun llmUsageSummaryFormat(a1: Long, a2: Long, a3: String): String
    val okay: String
    fun permissionAlwaysAllowConfirm(a1: String): String
    val permissionAlwaysAllowConfirmResetHint: String
    val permissionAlwaysAllowConfirmTitle: String
    val promptCategoryFavorites: String
    val promptCategoryGeneral: String
    val promptCategoryNotes: String
    val promptCategoryStudy: String
    val promptCategoryTest: String
    val promptCategoryUncategorized: String
    val promptModelDefault: String
    fun rawLlmLogEntryApiResponse(a1: Int): String
    val rawLlmLogEntryAssistant: String
    val rawLlmLogEntryEmpty: String
    val rawLlmLogEntrySystem: String
    fun rawLlmLogEntryTokens(a1: String): String
    fun rawLlmLogEntryToolCall(a1: String): String
    fun rawLlmLogEntryToolDefinitions(a1: Int): String
    fun rawLlmLogEntryToolDesc(a1: String): String
    fun rawLlmLogEntryToolParams(a1: String): String
    fun rawLlmLogEntryToolResult(a1: String): String
    fun rawLlmLogEntryUsage(a1: String, a2: String, a3: String): String
    val rawLlmLogEntryUser: String
    fun rawLlmLogTotal(a1: String, a2: String, a3: String): String
    fun rawLogItemTokens(a1: String, a2: String): String
    val selectAll: String
    val selectNone: String
    val toolCategoryBibleSearch: String
    val toolCategoryBookmarks: String
    val toolCategoryGeneralBooks: String
    val toolCategoryLabels: String
    val toolCategoryMyDocuments: String
    val toolCategoryStudyPads: String
    val toolCategoryWindows: String
    fun toolLogBookmarkCount(a1: Int): String
    fun toolLogCommentaryCount(a1: Int): String
    fun toolLogDocumentCount(a1: Int): String
    fun toolLogGenbookKeyCount(a1: Int, a2: Int): String
    fun toolLogLabelCount(a1: Int): String
    fun toolLogMyDocumentCount(a1: Int): String
    fun toolLogMyDocumentPageCount(a1: Int): String
    fun toolLogSearchResults(a1: Int, a2: Int): String
    fun toolLogStudyPadCount(a1: Int): String
    val toolLogWindowCopy: String
    fun toolLogWindowCount(a1: Int): String
    val toolLogWindowMinimized: String
    val toolLogWindowRemoved: String

    /** Localized tool name, or its code name when no display resource exists. */
    fun toolDisplayName(tool: AgentTool): String
    val defaultAgentSystemPrompt: String
    val defaultTextTransformationSystemPrompt: String
}
