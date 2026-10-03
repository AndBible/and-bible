package net.bible.sharedcore.ai.reading

import kotlinx.coroutines.flow.StateFlow

interface AgentSessionService {
    /** Current workspace's session snapshot; re-emitted on each agent event (bridged from ABEventBus). */
    val snapshot: StateFlow<AgentLogSnapshot>
    /** Cancel the running agent in the current workspace (AgentSessionManager.stopAgent). */
    fun stop()
    /** Configured models for the quick model-selector (default-first). */
    suspend fun configuredModels(): List<ReadingModelVd>
    /** Set the global default model (AiSettings.defaultModelId) — fires DefaultModelChangedEvent → snapshot re-emit. */
    fun setDefaultModel(modelId: String)
    fun autoHideEnabled(): Boolean                 // CommonUtils.aiSettings.autoHideAgentLogOnCompletion
    fun logVisiblePref(): Boolean                  // CommonUtils.settings "agent_log_widget_visible"
    fun setLogVisiblePref(v: Boolean)
    /** Active workspace id (for the "view raw" intent the host builds). */
    fun currentWorkspaceId(): String
    /** Force a rebuild (host calls on workspace switch). */
    fun refresh()
}
