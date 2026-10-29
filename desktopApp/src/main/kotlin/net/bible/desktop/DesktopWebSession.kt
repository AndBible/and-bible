package net.bible.desktop

import net.bible.sharedui.desktop.webengine.DesktopBibleWebViews
import net.bible.sharedui.desktop.webengine.DesktopWebEngine

/**
 * The app's single web-view owner: one [DesktopBibleWebViews] for the process (created outside composition, so a
 * recomposition never forks a second pane cache), released by [close] on window close.
 */
class DesktopWebSession(
    engine: DesktopWebEngine,
    private val shutdownEngine: () -> Unit,
    private val cleanUp: () -> Unit = {},
    log: (String) -> Unit = ::println,
) {
    val webViews = DesktopBibleWebViews(engine, log)
    private var closed = false

    /** Disposes every pane, then the engine, then [cleanUp]. Idempotent. */
    fun close() {
        if (closed) return
        closed = true
        try {
            webViews.disposeAll()
        } finally {
            try {
                shutdownEngine()
            } finally {
                cleanUp()
            }
        }
    }
}
