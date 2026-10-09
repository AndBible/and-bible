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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.bible.android.control.page.window.WindowControl
import net.bible.android.database.IdType
import net.bible.service.common.AiSettings
import net.bible.service.common.CommonUtils
import net.bible.service.db.DatabaseContainer
import net.bible.service.llm.LlmCostTracker
import net.bible.service.llm.LlmProvider
import net.bible.service.llm.agent.AgentLogEntry
import net.bible.service.llm.agent.AgentSessionChange
import net.bible.service.llm.agent.AgentSessionManager
import net.bible.service.llm.agent.AgentStopReason
import net.bible.service.llm.agent.LogEntryType
import net.bible.sharedcore.ai.reading.AgentLogEntryVd
import net.bible.sharedcore.ai.reading.AgentLogSnapshot
import net.bible.sharedcore.ai.reading.AgentSessionService
import net.bible.sharedcore.ai.reading.AgentStopReasonVd
import net.bible.sharedcore.ai.reading.LogEntryKind
import net.bible.sharedcore.ai.reading.LogEntryStatus
import net.bible.sharedcore.ai.reading.ReadingModelVd
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import net.bible.service.db.blockingDb

/**
 * Pure mapping from the mutable runtime [AgentLogEntry] to its immutable view-data twin. The two
 * enums are 1:1 by name ([LogEntryType]/[net.bible.service.llm.agent.EntryStatus] ↔
 * [LogEntryKind]/[LogEntryStatus]), verified in Task B1. `internal` so the test can call it directly
 * without going through a running [AgentSessionServiceImpl] instance.
 */
internal fun mapEntry(e: AgentLogEntry): AgentLogEntryVd = AgentLogEntryVd(
    id = e.id.toString(),
    kind = LogEntryKind.valueOf(e.type.name),
    status = LogEntryStatus.valueOf(e.status.name),
    message = e.message,
    details = e.details,
    cost = e.costInfo,
    showRawLogLink = e.showRawLogLink,
)

/** Names are 1:1 ([AgentStopReason] ↔ [AgentStopReasonVd]). */
internal fun AgentStopReason.toVd(): AgentStopReasonVd = AgentStopReasonVd.valueOf(name)

/**
 * Android impl of [AgentSessionService], bridging [AgentSessionManager.changes] and [AiSettings.defaultModelChanged]
 * into a [StateFlow] of an immutable [AgentLogSnapshot], scoped to the current workspace. The
 * `windowControl.windowRepository.id` lookup below was taken from the classic `AgentLogWidget`.
 *
 * Registered as a Koin single (lives for the process); the host calls [refresh] on workspace switch.
 */
class AgentSessionServiceImpl : AgentSessionService, KoinComponent {
    private val windowControl: WindowControl by inject()

    /** Always reads the current workspace ID so it stays correct after workspace switches. */
    private fun wsId(): IdType = windowControl.windowRepository.id

    /**
     * F127: terminal stop reason of the most recent [AgentSessionChange.StatusChanged], per workspace;
     * a start clears it. Only touched on Main: every [build] caller runs there.
     */
    private val lastStopByWorkspace = mutableMapOf<IdType, AgentStopReasonVd?>()

    private val _snapshot = MutableStateFlow(build())
    override val snapshot: StateFlow<AgentLogSnapshot> = _snapshot.asStateFlow()

    init {
        // Process lifetime (Koin single): never cancelled. onMain before, so subscribeOnMain now.
        AgentSessionManager.changes.subscribeOnMain { change ->
            // Recorded for every workspace, before the filter, so switching back finds its own reason.
            if (change is AgentSessionChange.StatusChanged) {
                lastStopByWorkspace[change.workspaceId] = change.stopReason?.toVd()
            }
            if (change.workspaceId != wsId()) return@subscribeOnMain
            when (change) {
                is AgentSessionChange.LogUpdated -> refresh()
                is AgentSessionChange.StatusChanged -> refresh()
                is AgentSessionChange.PermissionWaiting -> Unit // the panel never showed it
            }
        }
        // Process lifetime: never cancelled.
        AiSettings.defaultModelChanged.subscribeOnMain { refresh() }
    }

    private fun build(): AgentLogSnapshot {
        val workspaceId = wsId()
        val entries = AgentSessionManager.getLogEntries(workspaceId).map(::mapEntry)
        return AgentLogSnapshot(
            running = AgentSessionManager.isRunning(workspaceId),
            entries = entries,
            statusText = latestMeaningfulMessage(workspaceId),
            headerCost = AgentSessionManager.getSession(workspaceId)?.sessionCostUsd
                ?.takeIf { it > 0 }?.let { LlmCostTracker.formatCost(it) },
            defaultModelText = defaultModelLabel(),
            lastStopReason = lastStopByWorkspace[workspaceId],
        )
    }

    /**
     * Port of the classic `AgentLogWidget.getLatestMeaningfulMessage`: prefers the latest
     * ACTION/LLM_COMMENT entry (tool calls/comments are more informative than iteration INFO
     * chatter), else the latest non-INFO entry, else the very last entry.
     */
    private fun latestMeaningfulMessage(workspaceId: IdType): String? {
        val entries = AgentSessionManager.getLogEntries(workspaceId)
        val latestAction = entries.lastOrNull { it.type == LogEntryType.ACTION || it.type == LogEntryType.LLM_COMMENT }
        if (latestAction != null) return latestAction.message
        return entries.lastOrNull { it.type != LogEntryType.INFO }?.message
            ?: entries.lastOrNull()?.message
    }

    /** Port of the classic `AgentLogWidget.updateModelSelectorText` model-id lookup. */
    private fun defaultModelLabel(): String? {
        val defaultId = AiSettings.defaultModelId
        return defaultId?.let { id -> blockingDb { DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao().getById(id)?.modelId } }
    }

    override fun stop() {
        AgentSessionManager.stopAgent(wsId())
    }

    override suspend fun configuredModels(): List<ReadingModelVd> = withContext(Dispatchers.IO) {
        val defaultModelId = AiSettings.defaultModelId
        val modelDao = DatabaseContainer.instance.aiSettingsDb.llmConfiguredModelDao()
        val providerDao = DatabaseContainer.instance.aiSettingsDb.llmProviderConfigDao()

        val models = modelDao.all().sortedByDescending { it.id == defaultModelId }
        val providers = providerDao.all().associateBy { it.id }

        models.map { model ->
            ReadingModelVd(
                id = model.id.toString(),
                modelId = model.modelId,
                providerName = providers[model.providerConfigId]?.displayName ?: "?",
                isDefault = model.id == defaultModelId,
                supported = LlmProvider.isModelSupported(model.modelId),
            )
        }
    }

    override fun setDefaultModel(modelId: String) {
        AiSettings.defaultModelId = IdType(modelId)
    }

    override fun autoHideEnabled(): Boolean = CommonUtils.aiSettings.autoHideAgentLogOnCompletion

    override fun logVisiblePref(): Boolean = CommonUtils.settings.getBoolean(PREF_AGENT_LOG_VISIBLE, false)

    override fun setLogVisiblePref(v: Boolean) {
        CommonUtils.settings.setBoolean(PREF_AGENT_LOG_VISIBLE, v)
    }

    override fun currentWorkspaceId(): String = wsId().toString()

    override fun refresh() {
        _snapshot.value = build()
    }

    companion object {
        /** Verbatim classic key from `AgentLogWidget.PREF_AGENT_LOG_VISIBLE`. */
        private const val PREF_AGENT_LOG_VISIBLE = "agent_log_widget_visible"
    }
}
