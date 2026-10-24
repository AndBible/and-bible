package net.bible.sharedcore.platform.sync

/** Periodic wake-ups for a sync while the app is not running (iOS only). */
interface BackgroundRefresh {
    /** Must be called before the app finishes launching. The handler reports whether work succeeded. */
    fun register(handler: suspend () -> Boolean)
    fun schedule(earliestSeconds: Long)
    fun cancel()
}
