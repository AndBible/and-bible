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

    /**
     * The height the drag gesture currently in progress STARTED at, and whether there is one.
     *
     * Both are needed rather than one nullable field, because `null` is itself a meaningful start
     * height: "no dragged height yet, use the default". [dragGestureActive] therefore says whether
     * [heightAtDragGestureStart] means anything at all — and when it does not (a reducer driven
     * without the gesture boundary, e.g. a single-call test), the collapse branch falls back to
     * keeping the height the last step computed, which is what it did before the whole-branch review.
     */
    private var dragGestureActive: Boolean = false
    private var heightAtDragGestureStart: Float? = null

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
        // Round 12b §4: an auto-hide is a close too, so it forgets the dragged height. Keyed off the
        // transition to invisible rather than `newVisible == false` alone, so a snapshot arriving
        // while the panel is already hidden does not keep rewriting the same null.
        // An auto-hide forgets the height, so it must forget an in-flight gesture's stash too --
        // otherwise the tail of a gesture interrupted by the close could restore it (see `hide`).
        if (current.visible && !newVisible) forgetDragGesture()
        _state.update {
            if (it.visible && !newVisible) it.copy(snapshot = snap, visible = false, heightDp = null)
            else it.copy(snapshot = snap, visible = newVisible)
        }
    }

    private fun forgetDragGesture() {
        dragGestureActive = false
        heightAtDragGestureStart = null
    }

    fun toggleExpanded() {
        _state.update { it.copy(expanded = !it.expanded) }
    }

    /**
     * A drag gesture on the panel's handle has BEGUN (wired from `draggable`'s `onDragStarted`).
     *
     * Its whole job is to stash the height the gesture starts from, so [onHeightDrag]'s collapse
     * branch can restore it. Without that stash the panel had the whole-branch review's Blocker 1:
     * because [onHeightDrag] runs once per pointer-move event and each non-collapsing step overwrote
     * `heightDp` with the running value, a deliberate slow drag down walked a 400dp panel through
     * 360, 320, ..., 80 and then collapsed at 40 — leaving 80dp behind. Re-expanding gave a 4-20dp
     * log body, and the user's 400dp was gone. Only a single-event fling preserved a useful height.
     */
    fun onHeightDragStarted() {
        dragGestureActive = true
        heightAtDragGestureStart = _state.value.heightDp
    }

    /**
     * One drag step on the panel's handle. [dragUpDp] is POSITIVE upward — i.e. the direction that
     * grows the panel — so the caller converts the platform's downward-positive pointer delta once,
     * at the boundary, and this reducer reads the way the gesture feels.
     *
     * Every pointer step calls this reducer separately, and a non-collapsing step COMMITS its height:
     * mid-gesture values are therefore written to `heightDp` and then overwritten by the next step.
     * That is what makes the collapse branch's height the gesture's, not the last step's: it restores
     * the value [onHeightDragStarted] stashed. The rule the maintainer stated — the panel remembers
     * the height it was dragged to until it is CLOSED, and collapsing is not closing — is therefore
     * about the height the gesture ENDED a drag at (or, when the gesture collapsed the panel, the one
     * it began at), never about an intermediate pointer position.
     *
     * If no gesture boundary was reported (nothing called [onHeightDragStarted]), the collapse branch
     * keeps the last computed height instead — correct for a single-step call, and what the reducer
     * did before the fix.
     *
     * The upward branch is NOT reachable from a collapsed panel — the handle is rendered only
     * `if (state.expanded)`, so there is nothing to drag once it is shut (fix round 1, Minor 9
     * corrected the claim that there was). What it does is let a drag that started as a shrink be
     * reversed within the same gesture.
     */
    fun onHeightDrag(dragUpDp: Float, collapsedDp: Float, maxDp: Float) {
        val current = _state.value
        val from = agentPanelHeight(current, collapsedDp, maxDp)
        val requested = from + dragUpDp
        if (shouldCollapseAfterDrag(requested, collapsedDp)) {
            val remembered = if (dragGestureActive) heightAtDragGestureStart else current.heightDp
            _state.update { it.copy(expanded = false, heightDp = remembered) }
        } else {
            _state.update {
                it.copy(expanded = true, heightDp = clampAgentPanelHeight(requested, collapsedDp, maxDp))
            }
        }
    }

    fun show() {
        service.setLogVisiblePref(true)
        _state.update { it.copy(visible = true) }
    }

    fun hide() {
        service.setLogVisiblePref(false)
        // Round 12b §4: closing forgets the dragged height — a newly opened panel starts at the
        // default. These two lines are the whole implementation of "remembered until closed", and are
        // why the feature needs no new preference.
        //
        // The stash goes with it (whole-branch review, Blocker 1). A close can land mid-gesture — the
        // panel auto-hides itself on a terminal stop reason, and the trailing close/stop buttons sit
        // in the same surface — and a stash that outlived the close would let the gesture's remaining
        // pointer steps restore a height the panel has just discarded.
        forgetDragGesture()
        _state.update { it.copy(visible = false, heightDp = null) }
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
