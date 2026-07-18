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

import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.android.BibleApplication.Companion.application
import net.bible.android.activity.R
import net.bible.android.control.event.ABEventBus
import net.bible.android.control.event.onMain
import net.bible.android.view.activity.page.AppSettingsUpdated
import net.bible.service.common.AiSettings
import net.bible.service.common.CommonUtils
import net.bible.service.common.DefaultModelChangedEvent
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.agent.PermissionMode
import net.bible.sharedcore.ai.AiSettingsService
import net.bible.sharedcore.ai.AiSettingsSnapshot

/**
 * Android impl of [AiSettingsService]. Reads/writes the global [AiSettings] row (the classic
 * `CommonUtils.aiSettings` facade) and the AI DAOs / [LlmCostTracker], mirroring classic
 * `AiConnectionSettingsFragment` summary/refresh behavior exactly. Each setter writes through the
 * classic accessor then re-emits a freshly built [AiSettingsSnapshot]. Subscribes to the classic
 * broadcasts ([AppSettingsUpdated], [DefaultModelChangedEvent]) so external changes re-emit the
 * snapshot too (same bridge pattern as [net.bible.android.control.speak.SpeakSettingsServiceImpl]).
 * Registered as a Koin single (lives for the process); the host calls [refresh] in `onResume` for
 * parity with the old pull-based refresh.
 */
class AiSettingsServiceImpl : AiSettingsService {
    private val settings get() = CommonUtils.aiSettings
    private val providerDao get() = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()
    private val modelDao get() = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()

    private val _snapshot = MutableStateFlow(build())
    override val snapshot: StateFlow<AiSettingsSnapshot> = _snapshot.asStateFlow()

    init {
        ABEventBus.register(this) {
            onMain<AppSettingsUpdated> { refresh() }
            onMain<DefaultModelChangedEvent> { refresh() }
        }
    }

    private fun build(): AiSettingsSnapshot {
        val models = modelDao.all()
        val defaultModelId = settings.defaultModelId
        val defaultModel = models.firstOrNull { it.id == defaultModelId } ?: models.firstOrNull()

        return AiSettingsSnapshot(
            providerCount = providerDao.getCount(),
            providerNames = providerDao.all().map { it.displayName },
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
            ?: return application.getString(R.string.ai_language_app_default, Locale.getDefault().displayLanguage)
        val locale = Locale.forLanguageTag(tag)
        val displayName = locale.getDisplayLanguage(locale)
        return if (displayName.isNotEmpty() && displayName != tag) "$displayName ($tag)" else tag
    }

    /** Mirrors classic `AiConnectionSettingsFragment.updateUsageSummary`. */
    private fun usageSummary(): String {
        val totalUsage = LlmCostTracker.getTotalUsage()
        return if (totalUsage.totalTokens == 0L) {
            application.getString(R.string.llm_usage_summary_default)
        } else {
            application.getString(
                R.string.llm_usage_summary_format,
                totalUsage.inputTokens,
                totalUsage.outputTokens,
                LlmCostTracker.formatCost(LlmCostTracker.getTotalCost()),
            )
        }
    }

    private fun defaultPromptText(res: Int): String =
        application.resources.openRawResource(res).bufferedReader().use { it.readText() }

    override fun setAgentPermissionMode(value: String) {
        settings.agentPermissionMode = PermissionMode.valueOf(value)
        refresh()
    }

    override fun setAiLanguage(value: String) {
        settings.aiLanguage = value.ifBlank { null }
        refresh()
    }

    override fun setAskModelBeforeRun(value: Boolean) {
        settings.askModelBeforeRun = value
        refresh()
    }

    override fun setCommentaryMaxResponseTokens(value: Int) {
        settings.commentaryMaxResponseTokens = maxOf(0, value)
        refresh()
    }

    override fun setMaxIterations(value: Int) {
        settings.maxIterations = maxOf(0, value)
        refresh()
    }

    override fun setCustomAgentSystemPrompt(value: String?) {
        settings.customAgentSystemPrompt = value
        refresh()
    }

    override fun setCustomTextTransformationSystemPrompt(value: String?) {
        settings.customTextTransformationSystemPrompt = value
        refresh()
    }

    override fun setRawLogRetention(days: Int) {
        settings.rawLogRetentionDays = if (days > 0) days else null
        refresh()
    }

    override fun setAutoHideAgentLogOnCompletion(value: Boolean) {
        settings.autoHideAgentLogOnCompletion = value
        refresh()
    }

    override fun acceptDisclaimer() {
        settings.aiDisclaimerAccepted = true
        refresh()
    }

    override fun customAgentSystemPromptText(): String =
        settings.customAgentSystemPrompt?.takeIf { it.isNotBlank() }
            ?: defaultPromptText(R.raw.llm_agent_system_prompt)

    override fun customTextTransformationSystemPromptText(): String =
        settings.customTextTransformationSystemPrompt?.takeIf { it.isNotBlank() }
            ?: defaultPromptText(R.raw.llm_text_transformation_system_prompt)

    // Raw built-in defaults, ignoring any custom override — see AiSettingsService KDoc for why
    // these must stay distinct from the two methods above.
    override fun builtInAgentSystemPromptText(): String =
        defaultPromptText(R.raw.llm_agent_system_prompt)

    override fun builtInTextTransformationSystemPromptText(): String =
        defaultPromptText(R.raw.llm_text_transformation_system_prompt)

    override fun refresh() {
        _snapshot.value = build()
    }
}
