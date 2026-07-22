package net.bible.sharedcore.window

import kotlinx.coroutines.flow.StateFlow

/**
 * Reading-view chrome controller (iOS-clean). Re-exposes Batch 12a's window layout SSOT
 * and routes window commands through the injected [WindowCommands] seam. Grows in Plan B
 * (toolbar derivations) and Plan C (overflow menu model).
 */
class ReadingViewController(
    windowState: WindowStateService,
    private val commands: WindowCommands,
) {
    val layout: StateFlow<WindowLayoutState> = windowState.layout

    fun onWindowActivated(windowId: String) = commands.setActive(windowId)

    fun onSeparatorCommitted(id1: String, w1: Float, id2: String, w2: Float) =
        commands.commitWeights(id1, w1, id2, w2)
}
