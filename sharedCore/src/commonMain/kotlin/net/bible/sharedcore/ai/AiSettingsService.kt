package net.bible.sharedcore.ai

import kotlinx.coroutines.flow.StateFlow

/** Immutable snapshot of the AI settings the connection screen reads. */
data class AiSettingsSnapshot(
    val providerCount: Int,
    val providerNames: List<String> = emptyList(),
    val modelCount: Int,
    val defaultModelLabel: String?,        // e.g. "GPT-4o" or null
    val agentPermissionMode: String,       // AgentPermissionMode.name
    val aiLanguage: String,                // "" = app default
    val aiLanguageLabel: String,           // display label for the language row summary
    val askModelBeforeRun: Boolean,
    val commentaryMaxResponseTokens: Int,
    val maxIterations: Int,
    val customAgentSystemPromptSet: Boolean,
    val customTextTransformationSystemPromptSet: Boolean,
    val rawLogRetentionDays: Int,          // -1 = disabled (no retention limit / keep forever per classic)
    val autoHideAgentLogOnCompletion: Boolean,
    val allowedToolCount: Int,
    val deniedToolCount: Int,
    val usageSummary: String,              // pre-formatted usage/cost line
    val disclaimerAccepted: Boolean,
)

interface AiSettingsService {
    val snapshot: StateFlow<AiSettingsSnapshot>
    fun setAgentPermissionMode(value: String)
    fun setAiLanguage(value: String)
    fun setAskModelBeforeRun(value: Boolean)
    fun setCommentaryMaxResponseTokens(value: Int)
    fun setMaxIterations(value: Int)
    fun setCustomAgentSystemPrompt(value: String?)          // null/blank = reset to default
    fun setCustomTextTransformationSystemPrompt(value: String?)
    fun setRawLogRetention(days: Int)                       // -1 disables
    fun setAutoHideAgentLogOnCompletion(value: Boolean)
    fun acceptDisclaimer()
    fun customAgentSystemPromptText(): String               // current or default, for the editor
    fun customTextTransformationSystemPromptText(): String
    // Raw built-in default text (ignores any custom override) — used to detect an unedited save
    // (must NOT be confused with the two methods above, which return the CURRENT value when a
    // custom prompt is set; comparing a save against those would discard an unchanged custom
    // prompt, since the editor is prefilled with that same current value).
    fun builtInAgentSystemPromptText(): String
    fun builtInTextTransformationSystemPromptText(): String
    fun refresh()                                           // re-read (host calls on resume)
}
