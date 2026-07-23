package net.bible.sharedcore.ai.reading

enum class LogEntryKind { INFO, ACTION, PERMISSION_REQUEST, ERROR, LLM_COMMENT }
enum class LogEntryStatus { PENDING, APPROVED, DENIED, COMPLETED, FAILED }
enum class AgentStopReasonVd { COMPLETED, ERROR, CANCELLED }

data class AgentLogEntryVd(
    val id: String,
    val kind: LogEntryKind,
    val status: LogEntryStatus,
    val message: String,
    val details: String? = null,
    val cost: String? = null,
    val showRawLogLink: Boolean = false,
)

/** Immutable snapshot of the current workspace's agent session, rebuilt on each ABEventBus event. */
data class AgentLogSnapshot(
    val running: Boolean = false,
    val entries: List<AgentLogEntryVd> = emptyList(),
    val statusText: String? = null,        // latest meaningful message; null ⇒ show idle label
    val headerCost: String? = null,        // formatted session cost; null ⇒ hide
    val defaultModelText: String? = null,  // default model id; null ⇒ "not configured"
    val lastStopReason: AgentStopReasonVd? = null,  // set on a terminal status event; null while running/at start
)

/** Full UI state = service snapshot + UI-local visibility/expansion + optional model-picker list. */
data class AgentLogUiState(
    val visible: Boolean = false,
    val expanded: Boolean = false,
    val snapshot: AgentLogSnapshot = AgentLogSnapshot(),
    val modelPicker: List<ReadingModelVd>? = null,   // non-null ⇒ show the quick model-picker dialog
)

/** Pure replica of the :app `shouldAutoHideAgentLog` (net.bible.service.llm.agent) — hides on any
 *  non-error terminal reason when the setting is enabled; keeps visible on error; ignores start (null). */
fun shouldAutoHideAgentLog(settingEnabled: Boolean, reason: AgentStopReasonVd?): Boolean =
    settingEnabled && reason != null && reason != AgentStopReasonVd.ERROR
