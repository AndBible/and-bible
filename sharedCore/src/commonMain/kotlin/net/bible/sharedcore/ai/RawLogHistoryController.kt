package net.bible.sharedcore.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Staging brain for `RawLogHistoryActivity` (persisted raw-LLM-log list), backed directly by
 * [RawLogService.summaries] (already a `StateFlow`, so [summaries] is a pure pass-through) plus a
 * local multi-select set, mirroring the classic list's checkbox/contextual-action-bar selection.
 *
 * The host is responsible for calling [RawLogService.refresh] on resume / after external mutation
 * — this controller only forwards selection actions to [service].
 */
class RawLogHistoryController(
    private val service: RawLogService,
    private val scope: CoroutineScope,
    private val onOpenLog: (String) -> Unit,
) {
    val summaries: StateFlow<List<RawLogSummaryVd>> = service.summaries

    private val _selection = MutableStateFlow<Set<String>>(emptySet())
    val selection: StateFlow<Set<String>> = _selection.asStateFlow()

    /** Derived: true whenever [selection] is non-empty (classic's contextual selection mode). */
    val selectionMode: StateFlow<Boolean> =
        selection.map { it.isNotEmpty() }.stateIn(scope, SharingStarted.Eagerly, false)

    fun toggleSelect(id: String) {
        _selection.value = _selection.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    /** Deletes the currently selected records and exits selection mode. */
    fun deleteSelected() {
        service.deleteByIds(_selection.value)
        clearSelection()
    }

    fun deleteOlderThan(days: Int) = service.deleteOlderThan(days)

    fun deleteAll() = service.deleteAll()

    /** Navigate to the detail screen for [id] (host maps this to `RawLlmLogActivity`). */
    fun openLog(id: String) = onOpenLog(id)
}
