package net.bible.service.llm

import net.bible.android.database.IdType
import net.bible.service.llm.agent.PermissionMode
import net.bible.sharedcore.event.Events

interface AiSettings {
    val defaultModelChanged: Events<Unit>
    val configChanged: Events<Unit>
    fun notifyConfigChanged()
    var agentPermissionMode: PermissionMode
    var permanentlyAllowedTools: Set<AgentTool>
    var permanentlyDeniedTools: Set<AgentTool>
    var aiExcludedDocuments: Set<String>
    var commentaryMaxResponseTokens: Int
    var hiddenBuiltInPrompts: Set<IdType>
    var maxIterations: Int
    var commentaryDeselected: Set<String>
    var defaultModelId: IdType?
    var aiLanguage: String?
    var askModelBeforeRun: Boolean
    var aiDisclaimerAccepted: Boolean
    var hiddenBuiltInCategories: Set<IdType>
    var customAgentSystemPrompt: String?
    var customTextTransformationSystemPrompt: String?
    var favoritePrompts: Set<IdType>
    var rawLogRetentionDays: Int?
    var autoHideAgentLogOnCompletion: Boolean
    val aiDisplayLanguage: String
    suspend fun ensureLoaded()
}
