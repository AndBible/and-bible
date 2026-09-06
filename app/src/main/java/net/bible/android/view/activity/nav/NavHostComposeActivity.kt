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
package net.bible.android.view.activity.nav

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import net.bible.android.SharedConstants
import net.bible.android.activity.R
import net.bible.android.control.report.AiBugReport
import net.bible.android.control.report.ErrorReportControl
import net.bible.android.database.IdType
import net.bible.android.view.activity.ai.resolvedCustomPromptValue
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.AndBibleAddons
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.PromptCsvUtils
import net.bible.service.llm.PromptRepository
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.tools.Tool
import net.bible.service.llm.tools.ToolRegistry
import net.bible.service.sword.csvprompt.addCsvPromptBook
import net.bible.sharedcore.ai.AgentPermissionModeIds
import net.bible.sharedcore.ai.AiConnectionLabels
import net.bible.sharedcore.ai.AiConnectionSettingsController
import net.bible.sharedcore.ai.AiDocumentFilterController
import net.bible.sharedcore.ai.AiModelsController
import net.bible.sharedcore.ai.AiProvidersController
import net.bible.sharedcore.ai.AiPromptsController
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.DocumentFilterService
import net.bible.sharedcore.ai.GlobalToolPermissionsController
import net.bible.sharedcore.ai.LlmModelService
import net.bible.sharedcore.ai.LlmProviderService
import net.bible.sharedcore.ai.PromptEditController
import net.bible.sharedcore.ai.PromptService
import net.bible.sharedcore.ai.RawLlmLogController
import net.bible.sharedcore.ai.RawLogHistoryController
import net.bible.sharedcore.ai.RawLogService
import net.bible.sharedcore.ai.ToolPermissionService
import net.bible.sharedcore.ai.ToolVd
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.AbAppTheme
import net.bible.sharedui.ai.nav.AiConnectionSettingsDeps
import net.bible.sharedui.ai.nav.AiDocumentFilterDeps
import net.bible.sharedui.ai.nav.AiModelsDeps
import net.bible.sharedui.ai.nav.AiNavDeps
import net.bible.sharedui.ai.nav.AiProvidersDeps
import net.bible.sharedui.ai.nav.AiPromptsDeps
import net.bible.sharedui.ai.nav.GlobalToolPermissionsDeps
import net.bible.sharedui.ai.nav.PromptEditDeps
import net.bible.sharedui.ai.nav.RawLlmLogDeps
import net.bible.sharedui.ai.nav.RawLogHistoryDeps
import net.bible.sharedui.ai.nav.ToolInfoDeps
import net.bible.sharedui.ai.nav.aiNavGraph
import org.koin.android.ext.android.inject

/**
 * The single Android host for the Compose navigation graph. Screens migrated off their own
 * Activities become destinations inside it; `ScreenLauncher` routes to it by [EXTRA_ROUTE].
 *
 * It still extends [ActivityBase] on purpose: every migrated destination inherits the base's
 * theming, locale attachment, edge-to-edge setup, `CurrentActivityHolder` registration and — the
 * one that matters for this cluster — `awaitIntent`, which the SAF flows need.
 */
class NavHostComposeActivity : ActivityBase() {
    private val documentFilterService: DocumentFilterService by inject()
    private val toolPermissionService: ToolPermissionService by inject()
    private val llmModelService: LlmModelService by inject()
    private val aiSettingsService: AiSettingsService by inject()
    private val llmProviderService: LlmProviderService by inject()
    private val promptService: PromptService by inject()
    private val rawLogService: RawLogService by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startRoute = requireNotNull(intent.getStringExtra(EXTRA_ROUTE)) {
            "NavHostComposeActivity requires EXTRA_ROUTE — launch it via NavHostComposeActivity.intentFor()"
        }
        setContent {
            AbAppTheme {
                val navController = rememberNavController()
                val allTools = remember { ToolRegistry.getAllTools() }
                val aiDocumentFilterController = remember {
                    AiDocumentFilterController(service = documentFilterService, scope = lifecycleScope)
                }
                val globalToolPermissionsController = remember {
                    GlobalToolPermissionsController(service = toolPermissionService, scope = lifecycleScope)
                }
                val aiModelsController = remember {
                    AiModelsController(service = llmModelService, scope = lifecycleScope)
                }
                val aiConnectionSettingsController = remember {
                    AiConnectionSettingsController(
                        service = aiSettingsService,
                        scope = lifecycleScope,
                        labels = buildAiConnectionLabels(),
                        // The real navigation branching lives in aiNavGraph's
                        // AI_CONNECTION_SETTINGS arm (three edges are navController.navigate(...)),
                        // not here — see AiConnectionSettingsDeps' kdoc. This constructor param is
                        // required but unused: the screen's onNavigate is wired directly in the
                        // graph, never through controller::onNavigate.
                        onNavigate = {},
                    )
                }
                val aiProvidersController = remember {
                    AiProvidersController(service = llmProviderService, scope = lifecycleScope)
                }
                val deps = remember(allTools) {
                    AiNavDeps(
                        exitHost = { finish() },
                        toolInfo = ToolInfoDeps(
                            readTools = allTools.filter { !it.requiresPermission }.map { it.toToolVd() },
                            writeTools = allTools.filter { it.requiresPermission }.map { it.toToolVd() },
                            helpBody = getString(R.string.help_tool_info_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#ai-tools",
                        ),
                        aiDocumentFilter = AiDocumentFilterDeps(
                            controller = aiDocumentFilterController,
                            helpBody = getString(R.string.help_ai_document_filter_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-data-and-documents",
                        ),
                        globalToolPermissions = GlobalToolPermissionsDeps(
                            controller = globalToolPermissionsController,
                            helpBody = getString(R.string.help_global_tool_permissions_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#setting-permissions",
                        ),
                        aiModels = AiModelsDeps(
                            controller = aiModelsController,
                            providersForPicker = { llmModelService.providersForPicker() },
                            helpBody = getString(R.string.help_ai_models_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#available-models",
                            onResume = { llmModelService.refresh() },
                        ),
                        aiConnectionSettings = AiConnectionSettingsDeps(
                            controller = aiConnectionSettingsController,
                            languageChoices = buildAiLanguageChoices(),
                            customLanguageTag = CUSTOM_LANGUAGE_TAG,
                            onCustomPromptSave = { key, value -> onAiConnectionCustomPromptSave(key, value) },
                            customPromptTextFor = { key -> aiConnectionCustomPromptTextFor(key) },
                            onResetUsageConfirm = { showAiConnectionResetUsageConfirm() },
                            actions = { AiConnectionHelpAction() },
                            onResume = { aiSettingsService.refresh() },
                        ),
                        aiProviders = AiProvidersDeps(
                            controller = aiProvidersController,
                            helpBody = getString(R.string.help_ai_providers_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#choosing-a-provider",
                            unknownErrorMessage = getString(R.string.unknown_error),
                            onResume = { llmProviderService.refresh() },
                        ),
                        aiPrompts = AiPromptsDeps(
                            controllerFor = { onOpenPrompt, onNewPrompt, onOpenConnectionSettings ->
                                AiPromptsController(
                                    service = promptService,
                                    scope = lifecycleScope,
                                    onOpenPrompt = onOpenPrompt,
                                    onNewPrompt = onNewPrompt,
                                    onOpenConnectionSettings = onOpenConnectionSettings,
                                )
                            },
                            helpBody = getString(R.string.help_ai_settings_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
                            onImportCsv = ::importPrompts,
                            onExportCsv = ::exportPrompts,
                            onResume = { promptService.refresh() },
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
                                val globalMaxIterations = CommonUtils.aiSettings.maxIterations
                                if (globalMaxIterations <= 0) getString(R.string.prompt_max_iterations_unlimited)
                                else globalMaxIterations.toString()
                            },
                            helpBody = getString(R.string.help_prompt_edit_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html#custom-prompts",
                            onPromptCopied = {
                                Toast.makeText(this@NavHostComposeActivity, R.string.prompt_copied, Toast.LENGTH_SHORT).show()
                            },
                        ),
                        rawLlmLog = RawLlmLogDeps(
                            controllerFor = { RawLlmLogController(service = rawLogService, scope = lifecycleScope) },
                            defaultTitle = getString(R.string.raw_llm_log_title),
                            recordTitleFor = ::rawLlmLogRecordTitle,
                            onCopy = ::copyRawLlmLog,
                            onShare = ::shareRawLlmLog,
                            onDelete = { recordId -> rawLogService.deleteByIds(setOf(recordId)) },
                            onReportBug = ::reportRawLlmLogBug,
                        ),
                        rawLogHistory = RawLogHistoryDeps(
                            controllerFor = { onOpenLog ->
                                RawLogHistoryController(service = rawLogService, scope = lifecycleScope, onOpenLog = onOpenLog)
                            },
                            helpBody = getString(R.string.help_ai_connection_text),
                            helpReadMoreUrl = "https://docs.andbible.org/en/latest/ai.html",
                            onResume = { rawLogService.refresh() },
                        ),
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = startRoute,
                    // Paint an opaque themed ground BEHIND the graph. navigation-compose's default
                    // transition is a crossfade, and while both screens are semi-transparent the
                    // window background shows through — which on a dark or e-ink theme reads as a
                    // flash of the wrong colour on every navigation. AMR hit exactly this.
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    aiNavGraph(navController, deps)
                }
            }
        }
    }

    // --- AiConnectionSettings host baggage ----------------------------------------------------
    // Ported from the classic AiConnectionSettingsComposeActivity (not deleted — Task 10 does
    // that): Android-resource labels/locale arrays, the reset-usage AlertDialog, and the help
    // overflow. See AiConnectionSettingsDeps' kdoc for why each stays host-side rather than
    // moving into :sharedCore/:sharedUi.

    private fun buildAiConnectionLabels() = AiConnectionLabels(
        screenTitle = getString(R.string.ai_connection_settings),
        disclaimerWarningTitle = getString(R.string.ai_disclaimer_warning_title),
        disclaimerWarningSummary = getString(R.string.ai_disclaimer_warning_summary),
        gettingStartedTitle = getString(R.string.easy_setup_title),
        gettingStartedSummary = getString(R.string.easy_setup_pref_summary),
        providersModelsCategoryTitle = getString(R.string.ai_providers_models_category),
        providersTitle = getString(R.string.ai_providers_category),
        providersSummaryNone = getString(R.string.ai_providers_summary_none),
        modelsTitle = getString(R.string.ai_models_category),
        modelsSummaryNone = getString(R.string.ai_models_summary_none),
        behaviorCategoryTitle = getString(R.string.ai_behavior_category),
        agentPermissionModeTitle = getString(R.string.prompt_permission_mode),
        toolPermissionsTitle = getString(R.string.manage_tool_permissions_title),
        toolPermissionsSummary = getString(R.string.manage_tool_permissions_summary),
        documentsTitle = getString(R.string.ai_document_filter_title),
        documentsSummary = getString(R.string.ai_document_filter_summary),
        aiLanguageTitle = getString(R.string.ai_language_title),
        commentaryMaxResponseTitle = getString(R.string.commentary_max_response_title),
        commentaryMaxResponseNoLimit = getString(R.string.commentary_max_response_no_limit),
        commentaryMaxResponseValueFormat = getString(R.string.commentary_max_response_value),
        maxIterationsTitle = getString(R.string.agent_max_iterations_title),
        maxIterationsSummary = getString(R.string.agent_max_iterations_summary),
        maxIterationsUnlimitedSuffix = getString(R.string.prompt_max_iterations_unlimited),
        askModelBeforeRunTitle = getString(R.string.ask_model_before_run_title),
        askModelBeforeRunSummary = getString(R.string.ask_model_before_run_summary),
        autoHideAgentLogTitle = getString(R.string.auto_hide_agent_log_title),
        autoHideAgentLogSummary = getString(R.string.auto_hide_agent_log_summary),
        advancedCategoryTitle = getString(R.string.ai_advanced_category),
        customAgentSystemPromptTitle = getString(R.string.custom_agent_system_prompt_title),
        customTextTransformSystemPromptTitle = getString(R.string.custom_text_transform_system_prompt_title),
        customSystemPromptDefault = getString(R.string.custom_system_prompt_default),
        customSystemPromptCustom = getString(R.string.custom_system_prompt_custom),
        usageCategoryTitle = getString(R.string.ai_usage_category),
        usageSummaryTitle = getString(R.string.llm_usage_summary_title),
        resetUsageTitle = getString(R.string.llm_reset_usage_title),
        resetUsageSummary = getString(R.string.llm_reset_usage_summary),
        rawLogHistoryTitle = getString(R.string.raw_log_history_title),
        rawLogHistorySummary = getString(R.string.raw_log_history_summary),
        rawLogRetentionTitle = getString(R.string.raw_log_retention_title),
        rawLogRetentionSummaryDisabled = getString(R.string.raw_log_retention_summary_disabled),
        rawLogRetentionSummaryDaysFormat = getString(R.string.raw_log_retention_summary_days),
        permissionModeLabels = AgentPermissionModeIds.ordered.associateWith { id ->
            when (id) {
                "ALWAYS_ASK" -> getString(R.string.permission_always_ask)
                "ASK_ONCE_PER_RUN" -> getString(R.string.permission_ask_once_per_run)
                "ALLOW_ALL" -> getString(R.string.permission_allow_all)
                "DENY_ALL" -> getString(R.string.permission_deny_all)
                else -> id
            }
        },
    )

    /**
     * Builds the language option list from the `prefs_interface_locale_*` string-arrays — mirrors
     * classic `AiConnectionSettingsActivity.setupAiLanguage`'s option set (an "app default" entry,
     * one per non-empty locale code, and a trailing [CUSTOM_LANGUAGE_TAG] sentinel).
     */
    private fun buildAiLanguageChoices(): List<SettingsItem.Choice> {
        val descriptions = resources.getStringArray(R.array.prefs_interface_locale_descriptions)
        val codes = resources.getStringArray(R.array.prefs_interface_locale_values)
        val choices = mutableListOf<SettingsItem.Choice>()
        choices.add(
            SettingsItem.Choice(
                value = "",
                label = getString(R.string.ai_language_app_default, Locale.getDefault().displayLanguage),
            ),
        )
        for (i in codes.indices) {
            val code = codes[i]
            if (code.isNotEmpty()) choices.add(SettingsItem.Choice(value = code, label = descriptions[i]))
        }
        choices.add(SettingsItem.Choice(value = CUSTOM_LANGUAGE_TAG, label = getString(R.string.ai_language_custom)))
        return choices
    }

    /**
     * Reset-vs-blank parity with classic `showCustomSystemPromptEditor`, see
     * [resolvedCustomPromptValue]'s kdoc for why the comparison must be against the RAW built-in
     * default, never [aiConnectionCustomPromptTextFor]'s result.
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

    /** Per-model [LlmCostTracker.reset], ported from classic's `showResetUsageConfirm`. */
    private fun showAiConnectionResetUsageConfirm() {
        AlertDialog.Builder(this)
            .setTitle(R.string.llm_reset_usage_confirm_title)
            .setMessage(R.string.llm_reset_usage_confirm_message)
            .setPositiveButton(R.string.okay) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        for (model in DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao().all()) {
                            LlmCostTracker.reset(model.id)
                        }
                    }
                    aiSettingsService.refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /** Help overflow (parity with classic `ai_connection_options_menu`). */
    @Composable
    private fun RowScope.AiConnectionHelpAction() {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Text("⋮", fontSize = 24.sp) // vertical ellipsis; Material icons aren't on the app-module classpath
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = {
                expanded = false
                CommonUtils.showHelpDialog(
                    activity = this@NavHostComposeActivity,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_connection_text,
                    helpPath = "ai.html#getting-started",
                )
            })
        }
    }

    // --- RawLlmLog host baggage ---------------------------------------------------------------
    // Ported from classic RawLlmLogComposeActivity (not deleted — Task 10 does that): none of
    // this can move into commonMain (DatabaseContainer/SimpleDateFormat are Android-JVM-only; the
    // clipboard/share/AiBugReport calls are platform APIs) — see RawLlmLogDeps' kdoc.

    /** DB-mode title text, or `null` if the record is gone (mirrors classic `onCreate`'s title logic). */
    private suspend fun rawLlmLogRecordTitle(recordId: String): String? {
        val record = withContext(Dispatchers.IO) {
            DatabaseContainer.instance.aiSettingsDb.llmRawLogRecordDao().getById(IdType(recordId))
        } ?: return null
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return "${record.modelName} — ${dateFormat.format(Date(record.timestamp))}"
    }

    /** Mirrors classic `getLogText()`: DB text (as displayed, header included) or the session `format()`. */
    private fun rawLlmLogTextFor(recordId: String?, workspaceId: String?, recordText: String?): String {
        recordId?.let { return recordText ?: "" }
        val wid = workspaceId ?: return ""
        return AgentSessionManager.getSession(IdType(wid))?.rawLlmLog?.format() ?: ""
    }

    private fun copyRawLlmLog(recordId: String?, workspaceId: String?, recordText: String?) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Raw LLM Log", rawLlmLogTextFor(recordId, workspaceId, recordText)))
        Toast.makeText(this, R.string.raw_llm_log_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareRawLlmLog(recordId: String?, workspaceId: String?, recordText: String?) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, rawLlmLogTextFor(recordId, workspaceId, recordText))
            type = "text/plain"
        }
        startActivity(Intent.createChooser(sendIntent, getString(R.string.share)))
    }

    private fun reportRawLlmLogBug(recordId: String?, workspaceId: String?) {
        lifecycleScope.launch {
            if (recordId != null) {
                AiBugReport.reportAiBug(this@NavHostComposeActivity, IdType(recordId))
            } else {
                val wid = workspaceId ?: return@launch
                val log = AgentSessionManager.getSession(IdType(wid))?.rawLlmLog ?: return@launch
                AiBugReport.reportAiBugFromRawLog(this@NavHostComposeActivity, log)
            }
        }
    }

    // --- AiPrompts host baggage — the SAF (Storage Access Framework) seam ---------------------
    // Ported VERBATIM from classic AiPromptsComposeActivity (not deleted — Task 10 does that),
    // which itself mirrors classic AiSettingsActivity's exportPrompts/importPrompts exactly: the
    // editable-vs-addon chooser, the ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT intents, and the
    // result Toasts/error dialogs. None of `awaitIntent` (this Activity's ActivityBase suspend
    // bridge to the system file picker), AlertDialog.Builder, Toast, contentResolver or
    // SharedConstants.modulesDir has a commonMain equivalent — see AiPromptsDeps' kdoc.

    private suspend fun exportPrompts() {
        try {
            val dao = DatabaseContainer.instance.aiSettingsDb.agentPromptDao()
            val userPrompts = withContext(Dispatchers.IO) { dao.allPrompts() }

            if (userPrompts.isEmpty()) {
                Toast.makeText(this, getString(R.string.no_prompts_to_export), Toast.LENGTH_SHORT).show()
                return
            }

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/csv"
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
                putExtra(Intent.EXTRA_TITLE, "ai_prompts_$timestamp.csv")
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    withContext(Dispatchers.IO) {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            PromptCsvUtils.exportPromptsToCsv(outputStream, userPrompts)
                        } ?: throw IllegalArgumentException("Could not open output stream")
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.prompts_csv_export_success, userPrompts.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_AI_PROMPTS, "Error exporting prompts to CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.csv_export_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importPrompts() {
        val options = arrayOf(
            getString(R.string.import_prompts_editable),
            getString(R.string.import_prompts_addon),
        )
        val installAsAddon = suspendCancellableCoroutine<Boolean?> { cont ->
            AlertDialog.Builder(this)
                .setTitle(R.string.import_prompts_csv)
                .setItems(options) { _, which -> cont.resume(which == 1) }
                .setNegativeButton(R.string.cancel) { _, _ -> cont.resume(null) }
                .setOnCancelListener { cont.resume(null) }
                .show()
        } ?: return

        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "text/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/csv", "text/plain", "text/comma-separated-values"))
            }

            val result = awaitIntent(intent)
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    if (installAsAddon) {
                        installCsvAsAddon(uri)
                    } else {
                        importCsvAsEditable(uri)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_AI_PROMPTS, "Error importing prompts from CSV", e)
            ErrorReportControl.showErrorDialog(
                this,
                getString(R.string.csv_import_failed, e.message),
                exception = e
            )
        }
    }

    private suspend fun importCsvAsEditable(uri: Uri) {
        val importResult = withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                PromptCsvUtils.importPromptsFromCsv(inputStream)
            } ?: throw IllegalArgumentException("Could not open input stream")
        }

        if (importResult.errors > 0) {
            val message =
                getString(R.string.csv_import_errors, importResult.created, importResult.updated, importResult.errors) +
                    "\n\n" + importResult.errorMessages.take(5).joinToString("\n") +
                    if (importResult.errorMessages.size > 5) "\n..." else ""

            AlertDialog.Builder(this)
                .setTitle(getString(R.string.import_prompts_csv))
                .setMessage(message)
                .setPositiveButton(R.string.okay, null)
                .show()
        } else {
            Toast.makeText(
                this,
                getString(R.string.csv_import_success, importResult.created, importResult.updated),
                Toast.LENGTH_SHORT
            ).show()
        }

        promptService.refresh()
    }

    private suspend fun installCsvAsAddon(uri: Uri) {
        val displayName = contentResolver.query(uri, null, null, null, null)?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) it.getString(idx) else null
            } else null
        } ?: "prompts.csv"

        withContext(Dispatchers.IO) {
            val outDir = File(SharedConstants.modulesDir, "prompts")
            outDir.mkdirs()
            val outFile = File(outDir, displayName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            addCsvPromptBook(outFile)
            AndBibleAddons.clearCaches()
        }

        PromptRepository.clearAddonCache()
        Toast.makeText(this, R.string.install_zip_successfull, Toast.LENGTH_SHORT).show()
        promptService.refresh()
    }

    companion object {
        private const val TAG_AI_PROMPTS = "AiPromptsCompose"

        const val EXTRA_ROUTE: String = "nav_route"

        /** Sentinel identifying the "Custom…" entry in the AI-language picker (mirrors classic). */
        private const val CUSTOM_LANGUAGE_TAG = "\u0000custom"

        fun intentFor(context: Context, route: String): Intent =
            Intent(context, NavHostComposeActivity::class.java).putExtra(EXTRA_ROUTE, route)
    }
}

private fun Tool.toToolVd() = ToolVd(
    id = agentTool.name,
    displayName = ToolRegistry.getDisplayName(this),
    description = description,
    requiresPermission = requiresPermission,
    categoryId = category.name,
)
