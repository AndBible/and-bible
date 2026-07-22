package net.bible.sharedcore.window

/** Command seam the Compose reading view drives; the androidMain impl resolves ids to Windows. */
interface WindowCommands {
    /** Make the window with this opaque id the active window. */
    fun setActive(windowId: String)
    /** Commit new layout weights for two adjacent panes after a separator drag. */
    fun commitWeights(windowId1: String, weight1: Float, windowId2: String, weight2: Float)
}
