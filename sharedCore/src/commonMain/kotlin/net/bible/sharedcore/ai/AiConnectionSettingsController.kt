package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedcore.settings.SettingsScreenState

/** Navigation keys the host maps to `ScreenLauncher` screens, per [AiConnectionSettingsController.onNavigate]. */
object AiConnectionNav {
    const val EASY_SETUP = "easy_setup"
    const val PROVIDERS = "providers"
    const val MODELS = "models"
    const val TOOL_PERMISSIONS = "tool_permissions"
    const val DOCUMENTS = "documents"
    const val RAW_LOG_HISTORY = "raw_log_history"
}

/**
 * Host-resolved strings for the AI connection settings screen (titles/summaries/labels), kept out
 * of the controller so tests can supply stubs and translated strings stay on the Android side
 * (`strings.xml`).
 */
data class AiConnectionLabels(
    val screenTitle: String,
    val disclaimerWarningTitle: String,
    val gettingStartedTitle: String,
    val gettingStartedSummary: String,
    val providersModelsCategoryTitle: String,
    val providersTitle: String,
    val providersSummaryNone: String,
    val modelsTitle: String,
    val modelsSummaryNone: String,
    val behaviorCategoryTitle: String,
    val agentPermissionModeTitle: String,
    val toolPermissionsTitle: String,
    val toolPermissionsSummary: String,
    val documentsTitle: String,
    val documentsSummary: String,
    val aiLanguageTitle: String,
    val commentaryMaxResponseTitle: String,
    val commentaryMaxResponseNoLimit: String,
    val commentaryMaxResponseValueFormat: String,   // e.g. "%s tokens"; %s filled with formatted number
    val maxIterationsTitle: String,
    val maxIterationsSummary: String,
    val maxIterationsUnlimitedSuffix: String,
    val askModelBeforeRunTitle: String,
    val autoHideAgentLogTitle: String,
    val advancedCategoryTitle: String,
    val customAgentSystemPromptTitle: String,
    val customTextTransformSystemPromptTitle: String,
    val customSystemPromptDefault: String,
    val customSystemPromptCustom: String,
    val usageCategoryTitle: String,
    val usageSummaryTitle: String,
    val rawLogHistoryTitle: String,
    val rawLogRetentionTitle: String,
    val rawLogRetentionSummaryDisabled: String,
    val rawLogRetentionSummaryDaysFormat: String,   // e.g. "%d days"; %d filled with days
    val permissionModeLabels: Map<String, String>,  // AgentPermissionModeIds.ordered -> label
) {
    companion object {
        fun forTest() = AiConnectionLabels(
            screenTitle = "AI connection settings",
            disclaimerWarningTitle = "Disclaimer",
            gettingStartedTitle = "Getting started",
            gettingStartedSummary = "Set up your first AI provider",
            providersModelsCategoryTitle = "Providers & models",
            providersTitle = "Providers",
            providersSummaryNone = "No providers configured",
            modelsTitle = "Models",
            modelsSummaryNone = "No models configured",
            behaviorCategoryTitle = "Behavior",
            agentPermissionModeTitle = "Tool permission mode",
            toolPermissionsTitle = "Tool permissions",
            toolPermissionsSummary = "Manage tool permissions",
            documentsTitle = "Documents",
            documentsSummary = "Manage documents available to AI",
            aiLanguageTitle = "AI language",
            commentaryMaxResponseTitle = "Commentary max response",
            commentaryMaxResponseNoLimit = "No limit",
            commentaryMaxResponseValueFormat = "%s tokens",
            maxIterationsTitle = "Max iterations",
            maxIterationsSummary = "Max agent iterations",
            maxIterationsUnlimitedSuffix = "unlimited",
            askModelBeforeRunTitle = "Ask before running",
            autoHideAgentLogTitle = "Auto-hide agent log",
            advancedCategoryTitle = "Advanced",
            customAgentSystemPromptTitle = "Custom agent system prompt",
            customTextTransformSystemPromptTitle = "Custom text transformation system prompt",
            customSystemPromptDefault = "Default",
            customSystemPromptCustom = "Custom",
            usageCategoryTitle = "Usage",
            usageSummaryTitle = "Usage summary",
            rawLogHistoryTitle = "Raw log history",
            rawLogRetentionTitle = "Raw log retention",
            rawLogRetentionSummaryDisabled = "Disabled",
            rawLogRetentionSummaryDaysFormat = "%d days",
            permissionModeLabels = emptyMap(),
        )
    }
}

/**
 * Builds the declarative [SettingsScreenState] for the AI connection settings screen from an
 * [AiSettingsService] snapshot, applying the provider-presence visibility gate replicated from
 * classic `AiConnectionSettingsActivity`/`AiConnectionSettingsFragment`: with no providers
 * configured, only the disclaimer warning + "getting started" row are shown; once at least one
 * provider exists, the getting-started row hides and the provider/model shortcuts + behavior/
 * advanced/usage categories appear.
 */
class AiConnectionSettingsController(
    private val service: AiSettingsService,
    private val scope: CoroutineScope,
    private val labels: AiConnectionLabels,
    private val onNavigate: (String) -> Unit,
) {
    private val _state = MutableStateFlow(build(service.snapshot.value))
    val state: StateFlow<SettingsScreenState> = _state.asStateFlow()

    init {
        scope.launch { service.snapshot.collect { _state.value = build(it) } }
    }

    private fun build(s: AiSettingsSnapshot): SettingsScreenState {
        val hasProviders = s.providerCount > 0

        val providersSummary = if (s.providerCount > 0) {
            "${s.providerCount}"
        } else {
            labels.providersSummaryNone
        }
        val modelsSummary = if (s.modelCount > 0) {
            s.defaultModelLabel ?: "${s.modelCount}"
        } else {
            labels.modelsSummaryNone
        }
        val toolPermissionsSummary = if (s.allowedToolCount + s.deniedToolCount > 0) {
            "${labels.toolPermissionsSummary} (${s.allowedToolCount + s.deniedToolCount})"
        } else {
            labels.toolPermissionsSummary
        }
        val commentaryMaxResponseSummary = if (s.commentaryMaxResponseTokens <= 0) {
            labels.commentaryMaxResponseNoLimit
        } else {
            labels.commentaryMaxResponseValueFormat.replace("%s", s.commentaryMaxResponseTokens.toString())
        }
        val maxIterationsSummary = if (s.maxIterations <= 0) {
            "${labels.maxIterationsSummary} (${labels.maxIterationsUnlimitedSuffix})"
        } else {
            "${labels.maxIterationsSummary} (${s.maxIterations})"
        }
        val rawLogRetentionSummary = if (s.rawLogRetentionDays > 0) {
            labels.rawLogRetentionSummaryDaysFormat.replace("%d", s.rawLogRetentionDays.toString())
        } else {
            labels.rawLogRetentionSummaryDisabled
        }
        val customAgentPromptSummary =
            if (s.customAgentSystemPromptSet) labels.customSystemPromptCustom else labels.customSystemPromptDefault
        val customTextTransformPromptSummary =
            if (s.customTextTransformationSystemPromptSet) labels.customSystemPromptCustom else labels.customSystemPromptDefault

        val items = listOf(
            SettingsItem.InfoRow(
                key = "ai_disclaimer_warning",
                title = labels.disclaimerWarningTitle,
                visible = !s.disclaimerAccepted,
            ),
            SettingsItem.NavigationRow(
                key = AiConnectionNav.EASY_SETUP,
                title = labels.gettingStartedTitle,
                summary = labels.gettingStartedSummary,
                visible = !hasProviders,
            ),
            SettingsItem.Category(
                key = "ai_providers_models_category",
                title = labels.providersModelsCategoryTitle,
                visible = hasProviders,
            ),
            SettingsItem.NavigationRow(
                key = AiConnectionNav.PROVIDERS,
                title = labels.providersTitle,
                summary = providersSummary,
                visible = hasProviders,
            ),
            SettingsItem.NavigationRow(
                key = AiConnectionNav.MODELS,
                title = labels.modelsTitle,
                summary = modelsSummary,
                visible = hasProviders,
            ),
            SettingsItem.Category(
                key = "ai_behavior_category",
                title = labels.behaviorCategoryTitle,
                visible = hasProviders,
            ),
            SettingsItem.NavigationRow(
                key = AiConnectionNav.TOOL_PERMISSIONS,
                title = labels.toolPermissionsTitle,
                summary = toolPermissionsSummary,
                visible = hasProviders,
            ),
            SettingsItem.NavigationRow(
                key = AiConnectionNav.DOCUMENTS,
                title = labels.documentsTitle,
                summary = labels.documentsSummary,
                visible = hasProviders,
            ),
            SettingsItem.ListChoiceRow(
                key = "agent_permission_mode",
                title = labels.agentPermissionModeTitle,
                summary = labels.permissionModeLabels[s.agentPermissionMode] ?: s.agentPermissionMode,
                entries = agentPermissionModeChoices(labels.permissionModeLabels),
                selectedValue = s.agentPermissionMode,
                visible = hasProviders,
            ),
            SettingsItem.ListChoiceRow(
                key = "ai_language",
                title = labels.aiLanguageTitle,
                summary = s.aiLanguageLabel,
                entries = emptyList(),
                selectedValue = s.aiLanguage,
                visible = hasProviders,
            ),
            SettingsItem.TextInputRow(
                key = "commentary_max_response",
                title = labels.commentaryMaxResponseTitle,
                summary = commentaryMaxResponseSummary,
                value = s.commentaryMaxResponseTokens.toString(),
                numeric = true,
                visible = hasProviders,
            ),
            SettingsItem.TextInputRow(
                key = "agent_max_iterations",
                title = labels.maxIterationsTitle,
                summary = maxIterationsSummary,
                value = s.maxIterations.toString(),
                numeric = true,
                visible = hasProviders,
            ),
            SettingsItem.SwitchRow(
                key = "ask_model_before_run",
                title = labels.askModelBeforeRunTitle,
                checked = s.askModelBeforeRun,
                visible = hasProviders,
            ),
            SettingsItem.SwitchRow(
                key = "auto_hide_agent_log",
                title = labels.autoHideAgentLogTitle,
                checked = s.autoHideAgentLogOnCompletion,
                visible = hasProviders,
            ),
            SettingsItem.Category(
                key = "ai_advanced_category",
                title = labels.advancedCategoryTitle,
                visible = hasProviders,
            ),
            SettingsItem.TextInputRow(
                key = "custom_agent_prompt",
                title = labels.customAgentSystemPromptTitle,
                summary = customAgentPromptSummary,
                value = "",
                visible = hasProviders,
            ),
            SettingsItem.TextInputRow(
                key = "custom_text_transform_prompt",
                title = labels.customTextTransformSystemPromptTitle,
                summary = customTextTransformPromptSummary,
                value = "",
                visible = hasProviders,
            ),
            SettingsItem.Category(
                key = "ai_usage_category",
                title = labels.usageCategoryTitle,
                visible = hasProviders,
            ),
            SettingsItem.InfoRow(
                key = "usage_summary",
                title = labels.usageSummaryTitle,
                summary = s.usageSummary,
                visible = hasProviders,
            ),
            SettingsItem.NavigationRow(
                key = AiConnectionNav.RAW_LOG_HISTORY,
                title = labels.rawLogHistoryTitle,
                visible = hasProviders,
            ),
            SettingsItem.TextInputRow(
                key = "raw_log_retention",
                title = labels.rawLogRetentionTitle,
                summary = rawLogRetentionSummary,
                value = s.rawLogRetentionDays.toString(),
                numeric = true,
                visible = hasProviders,
            ),
        )
        return SettingsScreenState(title = labels.screenTitle, items = items)
    }

    fun onSwitch(key: String, checked: Boolean) {
        when (key) {
            "ask_model_before_run" -> service.setAskModelBeforeRun(checked)
            "auto_hide_agent_log" -> service.setAutoHideAgentLogOnCompletion(checked)
        }
    }

    fun onListChoice(key: String, value: String) {
        when (key) {
            "agent_permission_mode" -> service.setAgentPermissionMode(value)
            "ai_language" -> service.setAiLanguage(value)
        }
    }

    fun onTextInput(key: String, value: String) {
        when (key) {
            "custom_agent_prompt" -> service.setCustomAgentSystemPrompt(value)
            "custom_text_transform_prompt" -> service.setCustomTextTransformationSystemPrompt(value)
        }
    }

    fun onTextInputInt(key: String, value: Int) {
        when (key) {
            "commentary_max_response" -> service.setCommentaryMaxResponseTokens(value)
            "agent_max_iterations" -> service.setMaxIterations(value)
            "raw_log_retention" -> service.setRawLogRetention(value)
        }
    }

    fun onNavigate(key: String) = onNavigate.invoke(key)
}
