package net.bible.android.control.page.window

import net.bible.android.database.IdType
import net.bible.sharedcore.window.WindowCommands

/** androidMain impl of the reading-view command seam: opaque id -> Window -> WindowControl. */
class WindowCommandsImpl(private val windowControl: WindowControl) : WindowCommands {

    override fun setActive(windowId: String) {
        val window = windowControl.windowRepository.getWindow(IdType(windowId)) ?: return
        windowControl.activeWindow = window
    }

    override fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float) {
        val repo = windowControl.windowRepository
        val w1 = repo.getWindow(IdType(windowId1)) ?: return
        val w2 = repo.getWindow(IdType(windowId2)) ?: return
        w1.weight = weight1
        w2.weight = weight2
        windowControl.windowSizesChanged()
    }

    private fun window(id: String) = windowControl.windowRepository.getWindow(IdType(id))

    override fun addNewWindow(fromWindowId: String) { window(fromWindowId)?.let { windowControl.addNewWindow(it) } }
    override fun minimise(windowId: String) { window(windowId)?.let { windowControl.minimiseWindow(it) } }
    override fun close(windowId: String) { window(windowId)?.let { windowControl.closeWindow(it) } }
    override fun restore(windowId: String) { window(windowId)?.let { windowControl.restoreWindow(it) } }
    override fun maximise(windowId: String) { window(windowId)?.let { windowControl.maximiseWindow(it) } }
    override fun unMaximise() { windowControl.unMaximise() }
    override fun setPin(windowId: String, value: Boolean) { window(windowId)?.let { windowControl.setPinMode(it, value) } }
    override fun move(windowId: String, position: Int) { window(windowId)?.let { windowControl.moveWindow(it, position) } }
    override fun setSynchronised(windowId: String, value: Boolean) { window(windowId)?.let { windowControl.setSynchronised(it, value) } }
    override fun changeSyncGroup(windowId: String, group: Int) { window(windowId)?.let { windowControl.changeSyncGroup(it, group) } }
    override fun focusNext() { windowControl.focusNextWindow() }
    override fun focusPrevious() { windowControl.focusPreviousWindow() }
    override fun setRestoreButtonsVisible(value: Boolean) {
        val repo = windowControl.windowRepository
        repo.workspaceSettings.restoreButtonsVisible = value
        repo.notifyRestoreButtonsChanged()
    }
}
