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

import kotlinx.coroutines.sync.withLock
import net.bible.service.llm.collectOwned
import kotlinx.coroutines.launch
import net.bible.sharedcore.platform.AppCoroutineScope
import org.koin.core.component.inject
import org.koin.core.component.KoinComponent
import net.bible.service.llm.LlmStrings
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.service.llm.AiSettings
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.agent.PermissionMode
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.AiSettingsSnapshot

/**
 * Android impl of [AiSettingsService]. Reads/writes the global [AiSettings] row (the classic
 * `aiSettings` facade) and the AI DAOs / [LlmCostTracker], mirroring classic
 * `AiConnectionSettingsFragment` summary/refresh behavior exactly. Each setter writes through the
 * classic accessor then re-emits a freshly built [AiSettingsSnapshot]. Subscribes to
 * [AiSettings.configChanged] and [AiSettings.defaultModelChanged] so external changes re-emit the
 * snapshot too (same bridge pattern as [net.bible.android.control.speak.SpeakSettingsServiceImpl]).
 * Registered as a Koin single (lives for the process); the host calls [refresh] in `onResume` for
 * parity with the old pull-based refresh.
 */
class AiSettingsServiceImpl(private val strings: LlmStrings) : AiSettingsService, KoinComponent {
    private val settings: AiSettings by inject()
    private val appScope: AppCoroutineScope by inject()
    private val providerDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    private val _snapshot = MutableStateFlow(AiSettingsSnapshot(0, emptyList(), 0, null, "", "", "", false, 0, 0, false, false, -1, false, 0, 0, strings.llmUsageSummaryDefault, false))
    override val snapshot: StateFlow<AiSettingsSnapshot> = _snapshot.asStateFlow()

    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val refreshMutex = kotlinx.coroutines.sync.Mutex()

    init {
        appScope.launch { settings.ensureLoaded(); refresh() }
        // The application job owns subscriptions as well as refresh work.
        settings.configChanged.collectOwned(appScope) { refresh() }

        settings.defaultModelChanged.collectOwned(appScope) { refresh() }
    }

    private suspend fun build(): AiSettingsSnapshot {
        val (models, providerCount, providerNames) = run {
            Triple(modelDao.all(), providerDao.getCount(), providerDao.all().map { it.displayName })
        }
        val defaultModelId = settings.defaultModelId
        val defaultModel = models.firstOrNull { it.id == defaultModelId } ?: models.firstOrNull()

        return AiSettingsSnapshot(
            providerCount = providerCount,
            providerNames = providerNames,
            modelCount = models.size,
            defaultModelLabel = defaultModel?.modelId,
            agentPermissionMode = settings.agentPermissionMode.name,
            aiLanguage = settings.aiLanguage ?: "",
            aiLanguageLabel = aiLanguageLabel(),
            askModelBeforeRun = settings.askModelBeforeRun,
            commentaryMaxResponseTokens = settings.commentaryMaxResponseTokens,
            maxIterations = settings.maxIterations,
            customAgentSystemPromptSet = !settings.customAgentSystemPrompt.isNullOrBlank(),
            customTextTransformationSystemPromptSet = !settings.customTextTransformationSystemPrompt.isNullOrBlank(),
            rawLogRetentionDays = settings.rawLogRetentionDays ?: -1,
            autoHideAgentLogOnCompletion = settings.autoHideAgentLogOnCompletion,
            allowedToolCount = settings.permanentlyAllowedTools.size,
            deniedToolCount = settings.permanentlyDeniedTools.size,
            usageSummary = usageSummary(),
            disclaimerAccepted = settings.aiDisclaimerAccepted,
        )
    }

    /** Mirrors classic `AiConnectionSettingsFragment.updateAiLanguageSummary`. */
    private fun aiLanguageLabel(): String {
        val tag = settings.aiLanguage
            ?: return strings.aiLanguageAppDefault(Locale.getDefault().displayLanguage)
        val locale = Locale.forLanguageTag(tag)
        val displayName = locale.getDisplayLanguage(locale)
        return if (displayName.isNotEmpty() && displayName != tag) "$displayName ($tag)" else tag
    }

    /** Mirrors classic `AiConnectionSettingsFragment.updateUsageSummary`. */
    private suspend fun usageSummary(): String {
        val totalUsage = LlmCostTracker.getTotalUsage()
        return if (totalUsage.totalTokens == 0L) {
            strings.llmUsageSummaryDefault
        } else {
            strings.llmUsageSummaryFormat(
                totalUsage.inputTokens,
                totalUsage.outputTokens,
                LlmCostTracker.formatCost(LlmCostTracker.getTotalCost()),
            )
        }
    }

    override fun setAgentPermissionMode(value: String) {
        settings.agentPermissionMode = PermissionMode.valueOf(value)
        appScope.launch { refresh() }
    }

    override fun setAiLanguage(value: String) {
        settings.aiLanguage = value.ifBlank { null }
        appScope.launch { refresh() }
    }

    override fun setAskModelBeforeRun(value: Boolean) {
        settings.askModelBeforeRun = value
        appScope.launch { refresh() }
    }

    override fun setCommentaryMaxResponseTokens(value: Int) {
        settings.commentaryMaxResponseTokens = maxOf(0, value)
        appScope.launch { refresh() }
    }

    override fun setMaxIterations(value: Int) {
        settings.maxIterations = maxOf(0, value)
        appScope.launch { refresh() }
    }

    override fun setCustomAgentSystemPrompt(value: String?) {
        settings.customAgentSystemPrompt = value
        appScope.launch { refresh() }
    }

    override fun setCustomTextTransformationSystemPrompt(value: String?) {
        settings.customTextTransformationSystemPrompt = value
        appScope.launch { refresh() }
    }

    override fun setRawLogRetention(days: Int) {
        settings.rawLogRetentionDays = if (days > 0) days else null
        appScope.launch { refresh() }
    }

    override fun setAutoHideAgentLogOnCompletion(value: Boolean) {
        settings.autoHideAgentLogOnCompletion = value
        appScope.launch { refresh() }
    }

    override fun acceptDisclaimer() {
        settings.aiDisclaimerAccepted = true
        appScope.launch { refresh() }
    }

    override fun customAgentSystemPromptText(): String =
        settings.customAgentSystemPrompt?.takeIf { it.isNotBlank() }
            ?: strings.defaultAgentSystemPrompt

    override fun customTextTransformationSystemPromptText(): String =
        settings.customTextTransformationSystemPrompt?.takeIf { it.isNotBlank() }
            ?: strings.defaultTextTransformationSystemPrompt

    // Raw built-in defaults, ignoring any custom override — see AiSettingsService KDoc for why
    // these must stay distinct from the two methods above.
    override fun builtInAgentSystemPromptText(): String =
        strings.defaultAgentSystemPrompt

    override fun builtInTextTransformationSystemPromptText(): String =
        strings.defaultTextTransformationSystemPrompt

    override suspend fun refresh() = refreshMutex.withLock {
        _snapshot.value = build()
        _ready.value = true
    }
}

/**
 * Reset-vs-blank decision for a saved custom-prompt edit, matching classic
 * `showCustomSystemPromptEditor`'s `if (text == defaultPrompt) null else text.ifBlank { null }`:
 * store `null` (reset to the built-in default) when the typed [value] is blank/absent OR equals
 * the RAW BUILT-IN default (`builtInDefault`); otherwise store [value] as-is. `builtInDefault`
 * MUST be the raw built-in prompt text (e.g. [AiSettingsService.builtInAgentSystemPromptText]),
 * never the "current custom text" used to prefill the editor — comparing against the prefill
 * would make an unedited save of an existing custom prompt equal its own prefill and silently
 * discard it.
 *
 * Moved here from the classic `AiConnectionSettingsComposeActivity` (deleted in Task 10, which
 * ported the AI settings hub into the Compose nav graph): `NavHostComposeActivity` and
 * `AiConnectionSettingsComposeActivityTest` both need this top-level function to keep living in
 * the `net.bible.android.view.activity.ai` package after that Activity is gone (see
 * `AiNavGraph.kt`'s `AiConnectionSettingsDeps` KDoc for why it cannot move to `:sharedCore`).
 */
internal fun resolvedCustomPromptValue(value: String?, builtInDefault: String): String? =
    if (value.isNullOrBlank() || value == builtInDefault) null else value
