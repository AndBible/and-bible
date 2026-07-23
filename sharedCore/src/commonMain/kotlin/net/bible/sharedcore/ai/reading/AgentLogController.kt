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

    init {
        scope.launch {
            service.snapshot.collect { snap -> reduce(snap) }
        }
    }

    private fun reduce(snap: AgentLogSnapshot) {
        val current = _state.value
        val newVisible = when {
            snap.running && !current.visible -> {
                service.setLogVisiblePref(true)
                true
            }
            !snap.running && current.visible && shouldAutoHideAgentLog(service.autoHideEnabled(), snap.lastStopReason) -> {
                service.setLogVisiblePref(false)
                if (snap.lastStopReason == AgentStopReasonVd.COMPLETED) onCompletedToast()
                false
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
