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
}
