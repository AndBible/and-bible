@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package net.bible.sharedui.reading

import androidx.compose.ui.geometry.Rect

/** Physical overlap, so a wide rail and RTL layouts reserve every covered bottom pane. */
internal fun railCoveredWindows(panes: Map<String, Rect>, rail: Rect?): Set<String> =
    if (rail == null || rail.isEmpty) emptySet()
    else panes.filterValues { !it.isEmpty && it.overlaps(rail) }.keys

/** Keeps rail coverage separate from system insets, which belong to every bottom pane. */
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
