@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.app.ai

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.database.IdType
import net.bible.android.view.activity.ai.resolvedCustomPromptValue
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.AiSettings
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.PromptCsvUtils
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.sharedcore.ai.AgentPermissionModeIds
import net.bible.sharedcore.ai.AiConnectionLabels
import net.bible.sharedcore.ai.AiConnectionSettingsController
import net.bible.sharedcore.ai.AiDocumentFilterController
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.AiPromptsController
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.DocumentFilterService
import net.bible.sharedcore.ai.GlobalToolPermissionsController
import net.bible.sharedcore.ai.ImportMode
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.PromptEditController
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.RawLlmLogController
import net.bible.sharedcore.ai.RawLogHistoryController
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.docs.DocsLinks
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.AppCoroutineScope
import net.bible.sharedcore.platform.UserNotifier
import net.bible.sharedui.ai.nav.AiConnectionSettingsDeps
import net.bible.sharedui.ai.nav.AiDocumentFilterDeps
import net.bible.sharedui.ai.nav.AiModelsDeps
import net.bible.sharedui.ai.nav.AiNavDeps
import net.bible.sharedui.ai.nav.AiPromptsDeps
import net.bible.sharedui.ai.nav.AiProvidersDeps
import net.bible.sharedui.ai.nav.GlobalToolPermissionsDeps
import net.bible.sharedui.ai.nav.PromptEditDeps
import net.bible.sharedui.ai.nav.RawLlmLogDeps
import net.bible.sharedui.ai.nav.RawLogHistoryDeps
import net.bible.sharedui.ai.nav.ToolInfoDeps
import net.bible.sharedui.app.HostShell
import net.bible.sharedui.app.ports.ClipboardText
import net.bible.sharedui.app.ports.DocumentPicker
import net.bible.sharedui.app.ports.DocumentRef
import net.bible.sharedui.app.ports.HelpDialogs
import net.bible.sharedui.app.ports.HelpTopic
import net.bible.sharedui.app.ports.NoDocumentPickerException
import net.bible.sharedui.app.ports.TextShare
import net.bible.sharedui.components.AbMenuItem
import net.bible.sharedui.components.AbOverflowMenu
import net.bible.sharedui.strings.Strings
import okio.Buffer

private const val TAG_AI_PROMPTS = "AiPromptsCompose"

/**
 * The AI cluster's deps builder and host baggage: tool info, document filter, tool permissions, models,
 * connection settings, providers, prompts, prompt edit, the raw log and its history.
 *
 * Laziness is load-bearing. [navDeps] runs on every host launch, and the app's most common non-reading screens
 * (search, reading plan, settings) share that host, so none of this may run until an AI destination composes:
 * [AiModelsController]'s `init` starts a permanent collector on the host scope, [AiConnectionSettingsController]
 * builds its ~45 labels and starts a second collector that rebuilds the whole settings item tree, and the tool
 * lists walk the tool registry. Every service sits behind a provider and every controller and list behind a
 * `by lazy`. The controllers are fields rather than per-entry factories so that their collectors and snapshots
 * outlive one back-stack entry's composition.
 */
@kotlin.native.HiddenFromObjC
class AiHost(
    private val shell: HostShell,
    private val strings: Strings,
    private val textShare: TextShare,
    private val clipboard: ClipboardText,
    private val helpDialogs: HelpDialogs,
    private val documentPicker: DocumentPicker,
    private val promptFiles: PromptFiles,
    private val bugReport: BugReportPlatform,
    private val platform: AiHostPlatform,
    private val userNotifierProvider: () -> UserNotifier,
    private val appScopeProvider: () -> AppCoroutineScope,
    private val aiSettingsProvider: () -> AiSettings,
    private val aiSettingsServiceProvider: () -> AiSettingsService,
    private val llmModelServiceProvider: () -> LlmModelService,
    private val llmProviderServiceProvider: () -> LlmProviderService,
    private val promptServiceProvider: () -> PromptService,
    private val rawLogServiceProvider: () -> RawLogService,
    private val documentFilterServiceProvider: () -> DocumentFilterService,
    private val toolPermissionServiceProvider: () -> ToolPermissionService,
    private val allToolsProvider: () -> List<Tool> = { ToolRegistry.getAllTools() },
    private val toolViewData: (Tool) -> ToolVd = { it.toToolVd() },
) {
    private val aiSettingsService: AiSettingsService get() = aiSettingsServiceProvider()
    private val promptService: PromptService get() = promptServiceProvider()
    private val rawLogService: RawLogService get() = rawLogServiceProvider()
    private val notifier: UserNotifier get() = userNotifierProvider()

    private val aiModelsController by lazy {
        AiModelsController(service = llmModelServiceProvider(), scope = shell.hostScope)
    }

    private val aiConnectionSettingsController by lazy {
        AiConnectionSettingsController(
            service = aiSettingsService,
            scope = shell.hostScope,
            labels = buildAiConnectionLabels(),
            // The real navigation branching lives in aiNavGraph's AI_CONNECTION_SETTINGS arm (six of its seven edges
            // are navController.navigate(...); RESET_USAGE requests the controller's own dialog state), not here --
            // see AiConnectionSettingsDeps' kdoc. This constructor param is required but unused: the screen's
            // onNavigate is wired directly in the graph, never through controller::onNavigate.
            onNavigate = {},
            // The reset-usage question is the controller's own dialog state; confirmDialog() runs this callback,
            // which still needs DatabaseContainer/LlmCostTracker (no :sharedCore equivalent).
            onResetUsageConfirm = { resetAiConnectionUsage() },
        )
    }

    private val aiProvidersController by lazy {
        AiProvidersController(service = llmProviderServiceProvider(), scope = shell.hostScope)
    }

    /**
     * The tool registry, walked once and split the way `ToolInfoScreen` wants it. One `by lazy` behind the other two so
     * a host that never opens Tool info never calls into the registry at all.
     */
    private val allAiTools by lazy { allToolsProvider() }

    /** The tools the model may call without asking, as view data. */
    val readTools: List<ToolVd> by lazy { allAiTools.filter { !it.requiresPermission }.map(toolViewData) }

    /** The tools that need the user's permission, as view data. */
    val writeTools: List<ToolVd> by lazy { allAiTools.filter { it.requiresPermission }.map(toolViewData) }

    /**
     * The live [AiPromptsController] the AI_PROMPTS destination's arm built, for exactly as long as that destination's
     * composition is alive (see `AiPromptsDeps.onControllerLifecycle`'s kdoc). [importPrompts] reads it to await
     * [AiPromptsController.chooseImportMode]; [dismissImportModeChoice] reads it for the app-wide sheet overlay.
     */
    private var liveAiPromptsController: AiPromptsController? = null

    /**
     * An app-wide sheet request pre-empts a showing import-mode choice (two modal sheets must never stack); see
     * `AiPromptsDeps.onControllerLifecycle`'s kdoc. A no-op outside the AI_PROMPTS destination's lifetime.
     */
    fun dismissImportModeChoice() {
        liveAiPromptsController?.dismissImportModeChoice()
    }

    /** The AI cluster's deps. Everything behind it is lazy, so this touches no service and no database. */
    @Suppress("UNUSED_PARAMETER")
    fun navDeps(navController: NavHostController): AiNavDeps = AiNavDeps(
        exitHost = { shell.exitHost() },
        setWindowTitle = { title -> shell.setWindowTitle(title) },
        toolInfo = ToolInfoDeps(
            readTools = { readTools },
            writeTools = { writeTools },
            helpBody = { strings.helpToolInfoText },
            helpReadMoreUrl = DocsLinks.page("ai", "ai-tools"),
        ),
        aiDocumentFilter = AiDocumentFilterDeps(
            // A fresh instance per back-stack entry -- see AiDocumentFilterDeps' kdoc (C1: this used to be a single
            // remember{} at host scope, which is why "Discard changes?" did not actually discard anything).
            controllerFor = {
                AiDocumentFilterController(service = documentFilterServiceProvider(), scope = shell.hostScope)
            },
            helpBody = { strings.helpAiDocumentFilterText },
            helpReadMoreUrl = DocsLinks.page("ai", "available-data-and-documents"),
        ),
        globalToolPermissions = GlobalToolPermissionsDeps(
            // Same fix, same reason -- see AiDocumentFilterDeps' kdoc (C1).
            controllerFor = {
                GlobalToolPermissionsController(service = toolPermissionServiceProvider(), scope = shell.hostScope)
            },
            helpBody = { strings.helpGlobalToolPermissionsText },
            helpReadMoreUrl = DocsLinks.page("ai", "setting-permissions"),
        ),
        aiModels = AiModelsDeps(
            controller = { aiModelsController },
            providersForPicker = { llmModelServiceProvider().providersForPicker() },
            helpBody = { strings.helpAiModelsText },
            helpReadMoreUrl = DocsLinks.page("ai", "available-models"),
            onResume = { shell.hostScope.launch { llmModelServiceProvider().refresh() } },
        ),
        aiConnectionSettings = AiConnectionSettingsDeps(
            controller = { aiConnectionSettingsController },
            languageChoices = { platform.languageChoices() },
            customLanguageTag = AI_CUSTOM_LANGUAGE_TAG,
            onCustomPromptSave = { key, value -> onAiConnectionCustomPromptSave(key, value) },
            customPromptTextFor = { key -> aiConnectionCustomPromptTextFor(key) },
            actions = { AiConnectionHelpAction() },
            onResume = { shell.hostScope.launch { aiSettingsService.refresh() } },
        ),
        aiProviders = AiProvidersDeps(
            controller = { aiProvidersController },
            helpBody = { strings.helpAiProvidersText },
            helpReadMoreUrl = DocsLinks.page("ai", "choosing-a-provider"),
            unknownErrorMessage = { strings.unknownError },
            onResume = { shell.hostScope.launch { llmProviderServiceProvider().refresh() } },
        ),
        aiPrompts = AiPromptsDeps(
            controllerFor = { onOpenPrompt, onNewPrompt, onOpenConnectionSettings ->
                AiPromptsController(
                    service = promptService,
                    scope = shell.hostScope,
                    onOpenPrompt = onOpenPrompt,
                    onNewPrompt = onNewPrompt,
                    onOpenConnectionSettings = onOpenConnectionSettings,
                )
            },
            helpBody = { strings.helpAiSettingsText },
            helpReadMoreUrl = DocsLinks.page("ai"),
            // Launched on THIS host's scope, not a scope owned by the graph's composable arm -- see AiPromptsDeps'
            // kdoc for why: a rememberCoroutineScope() in that arm would be cancelled the instant its back-stack
            // entry stops being the top one (near-instant on Up/navigate), unlike the host scope (cancelled only at
            // the host's end), and interrupting installAsAddon's file-copy+DB sequence mid-way is strictly worse
            // than classic's behaviour.
            onImportCsv = { shell.hostScope.launch { importPrompts() } },
            onExportCsv = { shell.hostScope.launch { exportPrompts() } },
            onResume = { shell.hostScope.launch { promptService.refresh() } },
            // The live controller this arm builds, published for exactly as long as the AI_PROMPTS destination's
            // composition is alive -- see AiPromptsDeps.onControllerLifecycle's kdoc.
            onControllerLifecycle = { liveAiPromptsController = it },
            // The read half of the same field -- see AiPromptsDeps.currentController's kdoc.
            currentController = { liveAiPromptsController },
        ),
        promptEdit = PromptEditDeps(
            controllerFor = { promptId, template, defaultContext ->
                PromptEditController(
                    service = promptService,
                    promptId = promptId,
                    template = template,
                    defaultContext = defaultContext,
                )
            },
            categories = { promptService.categories() },
            toolsByCategory = { promptService.toolsByCategory() },
            modelChoices = { promptService.modelChoices() },
            globalToolPermission = { toolId -> promptService.globalToolPermission(toolId) },
            globalMaxIterationsLabel = {
                val globalMaxIterations = aiSettingsProvider().maxIterations
                if (globalMaxIterations <= 0) strings.promptMaxIterationsUnlimited
                else globalMaxIterations.toString()
            },
            helpBody = { strings.helpPromptEditText },
            helpReadMoreUrl = DocsLinks.page("ai", "custom-prompts"),
            onPromptCopied = { notifier.showInfo(strings.promptCopied) },
            onPromptCopyFailed = { notifier.showInfo(strings.promptCopyFailed) },
        ),
        rawLlmLog = RawLlmLogDeps(
            controllerFor = { RawLlmLogController(service = rawLogService, scope = shell.hostScope) },
            defaultTitle = { strings.rawLlmLogTitle },
            recordTitleFor = ::rawLlmLogRecordTitle,
            onCopy = ::copyRawLlmLog,
            onShare = ::shareRawLlmLog,
            onDelete = { recordId -> appScopeProvider().launch { rawLogService.deleteByIds(setOf(recordId)) } },
            onReportBug = ::reportRawLlmLogBug,
        ),
        rawLogHistory = RawLogHistoryDeps(
            controllerFor = { onOpenLog ->
                RawLogHistoryController(service = rawLogService, scope = shell.hostScope, onOpenLog = onOpenLog)
            },
            helpBody = { strings.helpAiConnectionText },
            helpReadMoreUrl = DocsLinks.page("ai"),
            onResume = { shell.hostScope.launch { rawLogService.refresh() } },
        ),
    )

    // --- AiConnectionSettings host baggage ----------------------------------------------------
    // Ported from the classic AiConnectionSettingsComposeActivity: the labels, the custom prompt editor hooks, the
    // reset-usage action and the help overflow. See AiConnectionSettingsDeps' kdoc for why each stays host-side.

    private fun buildAiConnectionLabels() = AiConnectionLabels(
        screenTitle = strings.connectionSettingsMenuLabel,
        disclaimerWarningTitle = strings.aiDisclaimerWarningTitle,
        disclaimerWarningSummary = strings.disclaimerWarningSummary,
        gettingStartedTitle = strings.easySetupTitle,
        gettingStartedSummary = strings.easySetupPrefSummary,
        providersModelsCategoryTitle = strings.aiProvidersModelsCategory,
        providersTitle = strings.aiProvidersTitle,
        providersSummaryNone = strings.aiProvidersSummaryNone,
        modelsTitle = strings.aiModelsTitle,
        modelsSummaryNone = strings.aiModelsSummaryNone,
        behaviorCategoryTitle = strings.aiBehaviorCategory,
        agentPermissionModeTitle = strings.promptPermissionModeLabel,
        toolPermissionsTitle = strings.manageToolPermissionsTitle,
        toolPermissionsSummary = strings.manageToolPermissionsSummary,
        documentsTitle = strings.aiDocumentFilterRowTitle,
        documentsSummary = strings.aiDocumentFilterSummary,
        aiLanguageTitle = strings.aiLanguageTitle,
        commentaryMaxResponseTitle = strings.commentaryMaxResponseTitle,
        commentaryMaxResponseNoLimit = strings.commentaryMaxResponseNoLimit,
        commentaryMaxResponseValueFormat = strings.commentaryMaxResponseValue,
        maxIterationsTitle = strings.agentMaxIterationsTitle,
        maxIterationsSummary = strings.agentMaxIterationsSummary,
        maxIterationsUnlimitedSuffix = strings.promptMaxIterationsUnlimited,
        askModelBeforeRunTitle = strings.askModelBeforeRunTitle,
        askModelBeforeRunSummary = strings.askModelBeforeRunSummary,
        autoHideAgentLogTitle = strings.autoHideAgentLogTitle,
        autoHideAgentLogSummary = strings.autoHideAgentLogSummary,
        advancedCategoryTitle = strings.aiAdvancedCategory,
        customAgentSystemPromptTitle = strings.customAgentSystemPromptTitle,
        customTextTransformSystemPromptTitle = strings.customTextTransformSystemPromptTitle,
        customSystemPromptDefault = strings.customSystemPromptDefault,
        customSystemPromptCustom = strings.customSystemPromptCustom,
        usageCategoryTitle = strings.aiUsageCategory,
        usageSummaryTitle = strings.llmUsageSummaryTitle,
        resetUsageTitle = strings.llmResetUsageTitle,
        resetUsageSummary = strings.llmResetUsageSummary,
        rawLogHistoryTitle = strings.rawLogHistoryTitle,
        rawLogHistorySummary = strings.rawLogHistorySummary,
        rawLogRetentionTitle = strings.rawLogRetentionTitle,
        rawLogRetentionSummaryDisabled = strings.rawLogRetentionDisabledLabel,
        rawLogRetentionSummaryDaysFormat = strings.rawLogRetentionSummaryDays,
        permissionModeLabels = AgentPermissionModeIds.ordered.associateWith { id ->
            when (id) {
                "ALWAYS_ASK" -> strings.permissionAlwaysAsk
                "ASK_ONCE_PER_RUN" -> strings.permissionAskOncePerRun
                "ALLOW_ALL" -> strings.permissionAllowAll
                "DENY_ALL" -> strings.permissionDenyAll
                else -> id
            }
        },
    )

    /**
     * Reset-vs-blank parity with classic `showCustomSystemPromptEditor`, see [resolvedCustomPromptValue]'s kdoc for why
     * the comparison must be against the RAW built-in default, never [aiConnectionCustomPromptTextFor]'s result.
     */
    private fun onAiConnectionCustomPromptSave(key: String, value: String?) {
        val builtInDefault = aiConnectionBuiltInPromptTextFor(key)
        when (key) {
            "custom_agent_prompt" ->
                aiSettingsService.setCustomAgentSystemPrompt(resolvedCustomPromptValue(value, builtInDefault))
            "custom_text_transform_prompt" ->
                aiSettingsService.setCustomTextTransformationSystemPrompt(resolvedCustomPromptValue(value, builtInDefault))
        }
    }

    /** Current custom text, or the built-in default (editor prefill only). */
    private fun aiConnectionCustomPromptTextFor(key: String): String = when (key) {
        "custom_agent_prompt" -> aiSettingsService.customAgentSystemPromptText()
        "custom_text_transform_prompt" -> aiSettingsService.customTextTransformationSystemPromptText()
        else -> ""
    }

    /** Raw built-in default text for [key] (ignores any custom override). */
    private fun aiConnectionBuiltInPromptTextFor(key: String): String = when (key) {
        "custom_agent_prompt" -> aiSettingsService.builtInAgentSystemPromptText()
        "custom_text_transform_prompt" -> aiSettingsService.builtInTextTransformationSystemPromptText()
        else -> ""
    }

    /**
     * Per-model [LlmCostTracker.reset], ported from classic's `showResetUsageConfirm`. The confirmation question lives
     * on [AiConnectionSettingsController]'s own `dialog` state; this IS the confirmed action, wired into that
     * controller's `onResetUsageConfirm`, and runs only from `confirmDialog()`.
     */
    private fun resetAiConnectionUsage() {
        appScopeProvider().launch {
            withContext(Dispatchers.IO) {
                for (model in DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao().all()) {
                    LlmCostTracker.reset(model.id)
                }
            }
            aiSettingsService.refresh()
        }
    }

    /** Help overflow (parity with classic `ai_connection_options_menu`). */
    @Composable
    private fun RowScope.AiConnectionHelpAction() {
        AbOverflowMenu { close ->
            AbMenuItem(text = strings.help, onClick = {
                close()
                helpDialogs.show(HelpTopic.AI_CONNECTION)
            })
        }
    }

    // --- RawLlmLog host baggage ---------------------------------------------------------------
    // Ported from classic RawLlmLogComposeActivity (see RawLlmLogDeps' kdoc).

    /** DB-mode title text, or `null` if the record is gone (mirrors classic `onCreate`'s title logic). */
    private suspend fun rawLlmLogRecordTitle(recordId: String): String? {
        val record = withContext(Dispatchers.IO) {
            DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(IdType(recordId))
        } ?: return null
        return "${record.modelName} — ${platform.formatLogTimestamp(record.timestamp)}"
    }

    /** Mirrors classic `getLogText()`: DB text (as displayed, header included) or the session `format()`. */
    private fun rawLlmLogTextFor(recordId: String?, workspaceId: String?, recordText: String?): String {
        recordId?.let { return recordText ?: "" }
        val wid = workspaceId ?: return ""
        return AgentSessionManager.getSession(IdType(wid))?.rawLlmLog?.format() ?: ""
    }

    internal fun copyRawLlmLog(recordId: String?, workspaceId: String?, recordText: String?) {
        clipboard.write("Raw LLM Log", rawLlmLogTextFor(recordId, workspaceId, recordText))
        notifier.showInfo(strings.rawLlmLogCopied)
    }

    internal fun shareRawLlmLog(recordId: String?, workspaceId: String?, recordText: String?) {
        textShare.shareText(rawLlmLogTextFor(recordId, workspaceId, recordText), strings.shareLabel)
    }

    private fun reportRawLlmLogBug(recordId: String?, workspaceId: String?) {
        shell.hostScope.launch {
            if (recordId != null) {
                bugReport.reportRecord(recordId)
            } else {
                val wid = workspaceId ?: return@launch
                val log = AgentSessionManager.getSession(IdType(wid))?.rawLlmLog ?: return@launch
                bugReport.reportRawLog(log)
            }
        }
    }

    // --- AiPrompts host baggage — the document picker seam --------------------------------------
    // Ported VERBATIM from classic AiPromptsComposeActivity, which itself mirrors classic AiSettingsActivity's
    // exportPrompts/importPrompts exactly: the pickers and the result messages. The editable-vs-addon chooser and the
    // post-import error summary are AiPromptsController's own dialog state.

    private suspend fun exportPrompts() {
        try {
            val dao = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
            val userPrompts = withContext(Dispatchers.IO) { dao.allPrompts() }

            if (userPrompts.isEmpty()) {
                notifier.showInfo(strings.noPromptsToExport)
                return
            }

            val ref = documentPicker.createDocument(promptFiles.suggestedExportName(), "text/csv")
            if (ref != null) {
                withContext(Dispatchers.IO) {
                    val buffer = Buffer()
                    PromptCsvUtils.exportPromptsToCsv(buffer, userPrompts)
                    promptFiles.write(ref, buffer.readUtf8())
                }
                notifier.showInfo(strings.promptsCsvExportSuccess(userPrompts.size))
            }
        } catch (e: Exception) {
            Log.e(TAG_AI_PROMPTS, "Error exporting prompts to CSV", e)
            // The Activity's own message when nothing could show a picker, as the intent launch threw it before.
            val message = (e as? NoDocumentPickerException)?.cause?.message ?: e.message
            platform.showErrorDialog(strings.csvExportFailed(message.toString()), e)
        }
    }

    /**
     * Classic's `.setItems(editable, add-on)` chooser is [AiPromptsController.chooseImportMode]:
     * [liveAiPromptsController] is null only if this somehow ran outside the AI_PROMPTS destination's lifetime (never
     * in production -- the button that invokes [importPrompts] lives on that same screen), in which case there is
     * nothing to ask and this just leaves.
     */
    private suspend fun importPrompts() {
        val controller = liveAiPromptsController ?: return
        val installAsAddon = controller.chooseImportMode() == ImportMode.ADDON

        try {
            val ref = documentPicker.openDocuments(
                listOf("text/csv", "text/plain", "text/comma-separated-values"),
                multiple = false,
            ).firstOrNull()
            if (ref != null) {
                val appScope = appScopeProvider()
                if (installAsAddon) {
                    withContext(appScope.coroutineContext + Dispatchers.Main.immediate) { installCsvAsAddon(ref) }
                } else {
                    withContext(appScope.coroutineContext + Dispatchers.Main.immediate) { importCsvAsEditable(ref, controller) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_AI_PROMPTS, "Error importing prompts from CSV", e)
            // The Activity's own message when nothing could show a picker, as the intent launch threw it before.
            val message = (e as? NoDocumentPickerException)?.cause?.message ?: e.message
            platform.showErrorDialog(strings.csvImportFailed(message.toString()), e)
        }
    }

    private suspend fun importCsvAsEditable(ref: DocumentRef, controller: AiPromptsController) {
        val importResult = withContext(Dispatchers.IO) {
            val text = promptFiles.read(ref)
            PromptCsvUtils.importPromptsFromCsv(Buffer().writeUtf8(text))
        }

        if (importResult.errors > 0) {
            val message =
                strings.csvImportErrors(importResult.created, importResult.updated, importResult.errors) +
                    "\n\n" + importResult.errorMessages.take(5).joinToString("\n") +
                    if (importResult.errorMessages.size > 5) "\n..." else ""

            // AiPromptsController's own dialog state, rendered by AiPromptsScreen as an AbErrorDialog.
            controller.showImportErrors(message)
        } else {
            notifier.showInfo(strings.csvImportSuccess(importResult.created, importResult.updated))
        }

        promptService.refresh()
    }

    private suspend fun installCsvAsAddon(ref: DocumentRef) {
        promptFiles.installAsAddon(ref)
        PromptRepository.clearAddonCache()
        notifier.showInfo(strings.installZipSuccessfull)
        promptService.refresh()
    }
}

/** The tool as the Tool info and prompt screens' view data. */
internal fun Tool.toToolVd() = ToolVd(
    id = agentTool.name,
    displayName = ToolRegistry.getDisplayName(this),
    description = description,
    requiresPermission = requiresPermission,
    categoryId = category.name,
)
