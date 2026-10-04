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

    fun onAddWindow(fromWindowId: String) = commands.addNewWindow(fromWindowId)
    fun onMinimise(windowId: String) = commands.minimise(windowId)
    fun onClose(windowId: String) = commands.close(windowId)
    fun onRestore(windowId: String) = commands.restore(windowId)
    fun onMaximise(windowId: String) = commands.maximise(windowId)
    fun onUnMaximise() = commands.unMaximise()
    fun onSetPin(windowId: String, value: Boolean) = commands.setPin(windowId, value)
    fun onMove(windowId: String, position: Int) = commands.move(windowId, position)
    fun onSetSynchronised(windowId: String, value: Boolean) = commands.setSynchronised(windowId, value)
    fun onChangeSyncGroup(windowId: String, group: Int) = commands.changeSyncGroup(windowId, group)
    fun onFocusNext() = commands.focusNext()
    fun onFocusPrevious() = commands.focusPrevious()
    fun onSetRestoreButtonsVisible(value: Boolean) = commands.setRestoreButtonsVisible(value)
}
