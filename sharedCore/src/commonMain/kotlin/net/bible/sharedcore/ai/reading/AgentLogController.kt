package net.bible.sharedcore.ai.reading

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State holder for the reading-view agent-log panel. Combines the [AgentSessionService] snapshot
 * with UI-local visible/expanded/model-picker state, replicating classic `AgentLogWidget`'s
 * auto-show (on run start) / auto-hide (on a non-error terminal reason, when enabled) behaviour.
 */
class AgentLogController(
    private val service: AgentSessionService,
    private val scope: CoroutineScope,
    private val onCompletedToast: () -> Unit,
    private val onOpenRawLog: () -> Unit,
) {
    private val _state = MutableStateFlow(
        AgentLogUiState(visible = service.logVisiblePref(), snapshot = service.snapshot.value)
    )
    val state: StateFlow<AgentLogUiState> = _state.asStateFlow()

    /**
     * Latches the terminal [AgentStopReasonVd] already acted on by the auto-hide/toast branch
     * below, so a replayed [AgentSessionService.snapshot] value (e.g. a fresh subscriber on
     * controller reconstruction, since [kotlinx.coroutines.flow.StateFlow] replays its current
     * value to every new collector) cannot re-fire the auto-hide + toast for a completion that
     * was already handled. Seeded from the snapshot already reflected in the initial [_state]
     * (constructed just above from [service]'s current value) — that value is what `init`'s
     * `collect` will replay first, and it must be treated as already-handled, not as a fresh
     * transition, or a controller rebuilt while a stale terminal snapshot + a manually-reopened
     * panel (`visible = true`) are both still around would spuriously hide+toast again on init.
     * Reset on every new run so a genuine subsequent stop is still handled once.
     */
    private var lastHandledStopReason: AgentStopReasonVd? =
        service.snapshot.value.takeIf { !it.running }?.lastStopReason

    init {
        scope.launch {
            service.snapshot.collect { snap -> reduce(snap) }
        }
    }

    private fun reduce(snap: AgentLogSnapshot) {
        val current = _state.value
        val newVisible = when {
            snap.running && !current.visible -> {
                lastHandledStopReason = null
                service.setLogVisiblePref(true)
                true
            }
            snap.running -> {
                lastHandledStopReason = null
                current.visible
            }
            snap.lastStopReason != null && snap.lastStopReason != lastHandledStopReason -> {
                lastHandledStopReason = snap.lastStopReason
                if (current.visible && shouldAutoHideAgentLog(service.autoHideEnabled(), snap.lastStopReason)) {
                    service.setLogVisiblePref(false)
                    if (snap.lastStopReason == AgentStopReasonVd.COMPLETED) onCompletedToast()
                    false
                } else {
                    current.visible
                }
            }
            else -> current.visible
        }
        _state.update { it.copy(snapshot = snap, visible = newVisible) }
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    fun show() {
        service.setLogVisiblePref(true)
        _state.update { it.copy(visible = true) }
    }

    fun hide() {
        service.setLogVisiblePref(false)
        _state.update { it.copy(visible = false) }
    }

    fun stop() {
        service.stop()
    }

    fun onModelSelectorClick() {
        scope.launch {
            val m = service.configuredModels()
            _state.update { it.copy(modelPicker = m) }
        }
    }

    fun onModelChosen(modelId: String) {
        service.setDefaultModel(modelId)
        _state.update { it.copy(modelPicker = null) }
    }

    fun onModelPickerDismiss() {
        _state.update { it.copy(modelPicker = null) }
    }

    fun onRawLogClick() {
        onOpenRawLog()
    }
}
