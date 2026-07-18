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
package net.bible.android.view.activity.ai

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.bible.android.activity.R
import net.bible.android.view.Screen
import net.bible.android.view.ScreenLauncher
import net.bible.android.view.activity.base.ActivityBase
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.device.ScreenSettings
import net.bible.service.llm.LlmCostTracker
import net.bible.sharedcore.ai.AiConnectionLabels
import net.bible.sharedcore.ai.AiConnectionNav
import net.bible.sharedcore.ai.AiConnectionSettingsController
import net.bible.sharedcore.ai.AgentPermissionModeIds
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.settings.SettingsItem
import net.bible.sharedui.ProvideAppLocals
import net.bible.sharedui.ai.AiConnectionSettingsScreen
import net.bible.sharedui.theme.AbTheme
import org.koin.android.ext.android.inject

/**
 * Reset-vs-blank decision for a saved custom-prompt edit, matching classic
 * `showCustomSystemPromptEditor`'s `if (text == defaultPrompt) null else text.ifBlank { null }`:
 * store `null` (reset to the built-in default) when the typed [value] is blank/absent OR equals
 * the RAW BUILT-IN default (`builtInDefault`); otherwise store [value] as-is. `builtInDefault`
 * MUST be the raw built-in prompt text (e.g. [AiSettingsService.builtInAgentSystemPromptText]),
 * never the "current custom text" used to prefill the editor — comparing against the prefill
 * would make an unedited save of an existing custom prompt equal its own prefill and silently
 * discard it.
 */
internal fun resolvedCustomPromptValue(value: String?, builtInDefault: String): String? =
    if (value.isNullOrBlank() || value == builtInDefault) null else value

/**
 * Compose host for the AI connection settings screen — the new-path twin of classic
 * [AiConnectionSettingsActivity]/[AiConnectionSettingsFragment]. Builds [AiConnectionLabels] from
 * `strings.xml`, wires the shared [AiConnectionSettingsController], and renders
 * [AiConnectionSettingsScreen]. The classic help overflow is preserved as a Compose top-bar action.
 *
 * The AI-language picker (F32) is now a Compose dialog rendered INSIDE [AiConnectionSettingsScreen]
 * (`AbListChoiceDialog`/`AbTextInputDialog`) — this host only reads the Android-resource locale
 * arrays ([buildLanguageChoices]) and hands the resulting `(value, label)` list + the "Custom..."
 * sentinel to the screen; the chosen value flows back through the existing `onListChoice` controller
 * callback, same as every other `ListChoiceRow`. The reset-usage confirmation (per-model
 * [LlmCostTracker.reset]) is the remaining Android-resource-backed dialog kept host-side. Custom-prompt
 * reset-vs-blank parity is resolved here in [onCustomPromptSave] because only the host knows the raw
 * built-in default text.
 */
class AiConnectionSettingsComposeActivity : ActivityBase() {
    private val service: AiSettingsService by inject()

    private val labels by lazy { buildLabels() }
    private val languageChoices by lazy { buildLanguageChoices() }

    private val controller by lazy {
        AiConnectionSettingsController(
            service = service,
            scope = lifecycleScope,
            labels = labels,
            onNavigate = { key -> onNavigate(key) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProvideAppLocals {
                AbTheme(
                    darkTheme = ScreenSettings.nightMode,
                    colorMode = CommonUtils.settings.displayColorMode,
                    disableAnimations = CommonUtils.settings.disableAnimations,
                ) {
                    val state by controller.state.collectAsState()
                    AiConnectionSettingsScreen(
                        state = state,
                        onUp = { finish() },
                        onSwitch = controller::onSwitch,
                        onListChoice = controller::onListChoice,
                        onTextInputInt = controller::onTextInputInt,
                        onCustomPromptSave = { key, value -> onCustomPromptSave(key, value) },
                        customPromptTextFor = { key -> customPromptTextFor(key) },
                        languageChoices = languageChoices,
                        customLanguageValue = customLanguageTag,
                        onNavigate = controller::onNavigate,
                        actions = { HelpAction() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Parity with classic's pull-based onResume refresh (summaries that don't broadcast, e.g.
        // provider/model counts changed in a child activity, tool-permission/document counts).
        service.refresh()
    }

    // --- Navigation --------------------------------------------------------------------------
    // Routed through ScreenLauncher (Screen enum) so PROVIDERS/MODELS honor the use_compose_ui
    // flag from this Compose host too. EASY_SETUP opens the Compose AiProvidersComposeActivity's
    // wizard (via EXTRA_START_EASY_SETUP) when the flag routes there; otherwise it falls back to
    // the classic AiProvidersActivity's plain provider list (classic has no ported easy-setup
    // wizard of its own — same interim fallback as 9a, just now flag-routed).
    private fun onNavigate(key: String) {
        when (key) {
            AiConnectionNav.EASY_SETUP -> launchEasySetup()
            AiConnectionNav.PROVIDERS -> startActivity(ScreenLauncher.intentFor(this, Screen.AiProviders))
            AiConnectionNav.MODELS -> startActivity(ScreenLauncher.intentFor(this, Screen.AiModels))
            AiConnectionNav.TOOL_PERMISSIONS -> startActivity(ScreenLauncher.intentFor(this, Screen.GlobalToolPermissions))
            AiConnectionNav.DOCUMENTS -> startActivity(ScreenLauncher.intentFor(this, Screen.AiDocumentFilter))
            AiConnectionNav.RAW_LOG_HISTORY -> startActivity(ScreenLauncher.intentFor(this, Screen.RawLogHistory))
            AiConnectionNav.RESET_USAGE -> showResetUsageConfirm()
        }
    }

    /**
     * Opens the AI-providers screen for [AiConnectionNav.EASY_SETUP]. When [ScreenLauncher] routes
     * [Screen.AiProviders] to the Compose host, adds [AiProvidersComposeActivity.EXTRA_START_EASY_SETUP]
     * so it opens straight into the Compose easy-setup wizard; when it routes to the classic
     * `AiProvidersActivity`, launches it plain (classic has no ported easy-setup wizard — same
     * interim fallback as 9a, just flag-routed).
     */
    private fun launchEasySetup() {
        val target = ScreenLauncher.targetFor(Screen.AiProviders)
        val intent = Intent(this, target)
        if (target == AiProvidersComposeActivity::class.java) {
            intent.putExtra(AiProvidersComposeActivity.EXTRA_START_EASY_SETUP, true)
        }
        startActivity(intent)
    }

    // --- Custom system prompts ---------------------------------------------------------------

    /**
     * Reset-vs-blank parity with classic `showCustomSystemPromptEditor`: the host knows the raw
     * built-in default, so if the saved text equals it (or is blank) we persist `null` (= default),
     * otherwise the literal text. Resolves the Task 6 deferred gap (the shared screen can't tell a
     * user-typed copy of the default from a genuine reset).
     *
     * IMPORTANT: the comparison MUST be against the RAW BUILT-IN default
     * ([builtInPromptTextFor]/[AiSettingsService.builtInAgentSystemPromptText]), never against
     * [customPromptTextFor]. The latter returns the CURRENT custom text when one is set (it's also
     * what prefills the editor), so comparing a save against it would make an unedited save of an
     * existing custom prompt equal its own prefill and silently discard it (data loss) — see
     * [resolvedCustomPromptValue].
     */
    private fun onCustomPromptSave(key: String, value: String?) {
        val builtInDefault = builtInPromptTextFor(key)
        when (key) {
            "custom_agent_prompt" ->
                service.setCustomAgentSystemPrompt(resolvedCustomPromptValue(value, builtInDefault))
            "custom_text_transform_prompt" ->
                service.setCustomTextTransformationSystemPrompt(resolvedCustomPromptValue(value, builtInDefault))
        }
    }

    /** Current custom text, or the built-in default (for the editor prefill only — NOT for the
     * reset-vs-blank comparison in [onCustomPromptSave], see its KDoc). */
    private fun customPromptTextFor(key: String): String = when (key) {
        "custom_agent_prompt" -> service.customAgentSystemPromptText()
        "custom_text_transform_prompt" -> service.customTextTransformationSystemPromptText()
        else -> ""
    }

    /** Raw built-in default text for [key] (ignores any custom override). */
    private fun builtInPromptTextFor(key: String): String = when (key) {
        "custom_agent_prompt" -> service.builtInAgentSystemPromptText()
        "custom_text_transform_prompt" -> service.builtInTextTransformationSystemPromptText()
        else -> ""
    }

    // --- AI language picker (locale arrays are Android-resource data → read here, rendered in
    // AiConnectionSettingsScreen via AbListChoiceDialog/AbTextInputDialog, F32) ----------------

    /** Sentinel value used to identify the "Custom…" entry in the language picker (mirrors classic). */
    private val customLanguageTag = " custom"

    /**
     * Builds the language option list from the `prefs_interface_locale_*` string-arrays: an "app
     * default" entry (value `""`, matching [net.bible.sharedcore.ai.AiSettingsService]'s "" = app
     * default convention), one entry per non-empty locale code, and a trailing [customLanguageTag]
     * sentinel entry ("Custom…") — mirrors classic `AiConnectionSettingsActivity.setupAiLanguage`'s
     * option set. The screen renders this via `AbListChoiceDialog`; picking the sentinel opens
     * `AbTextInputDialog` instead of committing it directly.
     */
    private fun buildLanguageChoices(): List<SettingsItem.Choice> {
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
        choices.add(SettingsItem.Choice(value = customLanguageTag, label = getString(R.string.ai_language_custom)))
        return choices
    }

    // --- Reset usage (per-model LlmCostTracker.reset) ----------------------------------------

    private fun showResetUsageConfirm() {
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
                    service.refresh()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // --- Help overflow (parity with classic ai_connection_options_menu) ----------------------

    @Composable
    private fun RowScope.HelpAction() {
        var expanded by remember { mutableStateOf(false) }
        IconButton(onClick = { expanded = true }) {
            Text("⋮", fontSize = 24.sp) // vertical ellipsis; Material icons aren't on the app-module classpath
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(getString(R.string.help)) }, onClick = {
                expanded = false
                CommonUtils.showHelpDialog(
                    activity = this@AiConnectionSettingsComposeActivity,
                    titleResId = R.string.help,
                    messageResId = R.string.help_ai_connection_text,
                    helpPath = "ai.html#getting-started",
                )
            })
        }
    }

    private fun buildLabels() = AiConnectionLabels(
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
}
