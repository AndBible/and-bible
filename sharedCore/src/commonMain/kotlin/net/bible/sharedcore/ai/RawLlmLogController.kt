package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Staging brain for `RawLlmLogActivity` (detail view of one raw-LLM-conversation log), which has
 * two mutually-exclusive load modes mirroring the classic activity's two entry points:
 *
 * - **DB mode** ([loadRecord]): a persisted, gzip-decompressed record — plain, non-expandable text
 *   in [recordText]. [entries] stays empty.
 * - **In-memory mode** ([loadSession]): the live, expandable entries of an active `AgentSession` in
 *   [entries]. [recordText] stays null.
 *
 * The host picks exactly one of [loadRecord]/[loadSession] depending on how the screen was opened
 * (a `recordId` extra vs a `workspaceId` extra). [expandedIndices] tracks which [entries] rows are
 * expanded (in-memory mode only; DB-mode text has no per-entry expansion). Copy/share/delete/report-
 * bug are host-nav/action lambdas wired at the host level — this controller only exposes the text/
 * entries plus [canReportBug] so the host knows whether to enable that action.
 */
class RawLlmLogController(
    private val service: RawLogService,
    private val scope: CoroutineScope,
) {
    private val _recordText = MutableStateFlow<String?>(null)
    val recordText: StateFlow<String?> = _recordText.asStateFlow()

    private val _entries = MutableStateFlow<List<RawLogEntryVd>>(emptyList())
    val entries: StateFlow<List<RawLogEntryVd>> = _entries.asStateFlow()

    private val _expandedIndices = MutableStateFlow<Set<Int>>(emptySet())
    val expandedIndices: StateFlow<Set<Int>> = _expandedIndices.asStateFlow()

    private val _canReportBug = MutableStateFlow(false)
    val canReportBug: StateFlow<Boolean> = _canReportBug.asStateFlow()

    /** DB mode: loads [RawLogService.recordText] for [recordId] (null if the record is gone). */
    fun loadRecord(recordId: String) {
        scope.launch {
            _recordText.value = service.recordText(recordId)
            _canReportBug.value = service.canReportBug(recordId)
        }
    }

    /** In-memory mode: loads the active session's [RawLogService.sessionEntries] for [workspaceId]. */
    fun loadSession(workspaceId: String) {
        scope.launch {
            _entries.value = service.sessionEntries(workspaceId)
            _canReportBug.value = service.canReportBug(null)
        }
    }

    fun toggleExpanded(index: Int) {
        _expandedIndices.value = _expandedIndices.value.let { current ->
            if (index in current) current - index else current + index
        }
    }
}
