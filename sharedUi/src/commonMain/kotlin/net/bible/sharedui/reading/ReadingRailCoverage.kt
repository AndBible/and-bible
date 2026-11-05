@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading

/** Tracks bottom panes under the expanded full-width rail, independently of their system insets. */
@kotlin.native.HiddenFromObjC
class ReadingRailCoverage(private val onChanged: () -> Unit = {}) {
    private var coveredWindows: Set<String> = emptySet()

    fun update(windowIds: Set<String>) = update(windowIds, bottomWindows)

    private var bottomWindows: Set<String>? = null

    /**
     * The windows touching the bottom edge in the actual split layout, or null while no layout has reported (or after it left
     * composition). Once known it replaces the host's own portrait rule, which can disagree with the Compose split axis.
     */
    fun updateBottomWindows(windowIds: Set<String>?) = update(coveredWindows, windowIds)

    /** Both facts of one layout pass in a single change notification (one `set_offsets` per layout transition). */
    fun update(covered: Set<String>, bottom: Set<String>?) {
        if (coveredWindows != covered || bottomWindows != bottom) {
            coveredWindows = covered.toSet()
            bottomWindows = bottom?.toSet()
            onChanged()
        }
    }

    /** [isBottomWindow] is the host's fallback rule, used only until [updateBottomWindows] has reported a layout. */
    fun bottomOffset(windowId: String, isBottomWindow: Boolean, systemInset: Float, railHeight: Float): Float {
        val atBottom = bottomWindows?.let { windowId in it } ?: isBottomWindow
        return if (!atBottom) 0f else systemInset + if (windowId in coveredWindows) railHeight else 0f
    }
}
