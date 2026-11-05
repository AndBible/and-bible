@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading

/** Tracks bottom panes under the expanded full-width rail, independently of their system insets. */
@kotlin.native.HiddenFromObjC
class ReadingRailCoverage(private val onChanged: () -> Unit = {}) {
    private var coveredWindows: Set<String> = emptySet()

    fun update(windowIds: Set<String>) {
        if (coveredWindows != windowIds) {
            coveredWindows = windowIds.toSet()
            onChanged()
        }
    }

    fun bottomOffset(windowId: String, isBottomWindow: Boolean, systemInset: Float, railHeight: Float): Float =
        if (!isBottomWindow) 0f else systemInset + if (windowId in coveredWindows) railHeight else 0f
}
