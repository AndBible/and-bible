package net.bible.service.history

import net.bible.android.control.page.window.Window

/**
 * The platform decisions [HistoryManager] cannot make itself. The port lives in :app beside its
 * consumer because [Window] and [HistoryItem] are :app types (no module moves in L1a).
 */
interface HistoryPlatform {
    /**
     * A screen (non-reading) history item for [window], or null if none applies. With a non-null
     * [screenToken] (on Android the Intent a screen leaves with) the item is built from it; with
     * null it is built from the screen currently on top, if that screen takes part in history.
     */
    fun screenHistoryItem(window: Window, screenToken: Any?): HistoryItem?

    /** True when the reading view is what the user is looking at. */
    fun isOnReadingScreen(): Boolean

    /** Leaves the screen on top (finish / pop), used when going back from a non-reading screen. */
    fun leaveCurrentScreen()
}
