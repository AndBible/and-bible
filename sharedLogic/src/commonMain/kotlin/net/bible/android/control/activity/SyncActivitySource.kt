package net.bible.android.control.activity

import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription

/**
 * Shows cloud sync in the [ActivityCenter]: one indeterminate `sync` item while it runs (a successful sync
 * leaves no row), and failures through [reportFailure].
 */
class SyncActivitySource(
    private val center: ActivityCenter,
    running: Events<Boolean>,
    /** Resolved when a sync starts, not at construction: the source is created eagerly, before any UI strings exist. */
    private val syncingText: () -> String,
) {
    private val subscription: Subscription = running.subscribe { isRunning ->
        if (isRunning) {
            if (!center.isRunning(ID)) center.start(ID, ActivityKind.SYNC, syncingText())
        } else {
            center.succeed(ID)
        }
    }

    fun close() = subscription.cancel()

    /** Records a failed sync. Failures with an identical [message] share one row, which is refreshed and made unread again. */
    fun reportFailure(message: String) {
        val id = "sync-failed:${message.hashCode()}"
        center.start(id, ActivityKind.SYNC, message)
        center.fail(id, message)
    }

    private companion object {
        const val ID = "sync"
    }
}
