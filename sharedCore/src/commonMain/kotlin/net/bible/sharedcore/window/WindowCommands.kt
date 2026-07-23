package net.bible.sharedcore.window

/**
 * Command seam the Compose reading view drives; the androidMain impl resolves ids to Windows.
 *
 * Grown (Batch 12b follow-on, Plan A) beyond the original `setActive`/`commitWeights` pair to
 * the full window-management command set: add/minimise/close/restore/maximise/unMaximise,
 * pin/move/sync-group, focus-next/previous, and the restore-buttons visibility toggle.
 */
interface WindowCommands {
    /** Make the window with this opaque id the active window. */
    fun setActive(windowId: String)
    /** Commit new layout weights for two adjacent panes after a separator drag. */
    fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float)
    /** Add a new window, cloning settings from the window with this opaque id. */
    fun addNewWindow(fromWindowId: String)
    /** Minimise the window with this opaque id. */
    fun minimise(windowId: String)
    /** Close the window with this opaque id. */
    fun close(windowId: String)
    /** Restore (un-minimise) the window with this opaque id. */
    fun restore(windowId: String)
    /** Maximise the window with this opaque id, hiding all other panes. */
    fun maximise(windowId: String)
    /** Clear the maximised window, returning to the normal split layout. */
    fun unMaximise()
    /** Set whether the window with this opaque id is pinned. */
    fun setPin(windowId: String, value: Boolean)
    /** Move the window with this opaque id to a new position in its pinned/unpinned group. */
    fun move(windowId: String, position: Int)
    /** Set whether the window with this opaque id is synchronised (scroll-linked). */
    fun setSynchronised(windowId: String, value: Boolean)
    /** Move the window with this opaque id into the given synchronisation group. */
    fun changeSyncGroup(windowId: String, group: Int)
    /** Move focus to the next visible window. */
    fun focusNext()
    /** Move focus to the previous visible window. */
    fun focusPrevious()
    /** Set whether the restore-buttons rail is visible. */
    fun setRestoreButtonsVisible(value: Boolean)
}
