package net.bible.android.control.activity

import net.bible.sharedcore.event.Events
import net.bible.sharedcore.event.Subscription

/**
 * Shows cloud sync in the [ActivityCenter]: one indeterminate `sync` item while it runs (a successful sync
 * leaves no row), and failures of any kind through [reportFailure].
 */
class SyncActivitySource(
    private val center: ActivityCenter,
    running: Events<Boolean>,
    /** Resolved when a sync starts, not at construction: the source is created eagerly, before any UI strings exist. */
    private val syncingText: () -> String,
) {
    private val subscription: Subscription = running.subscribe { isRunning ->
        if (isRunning) {
            center.startOrUpdate(ID, ActivityKind.SYNC, syncingText(), null, null)
        } else {
            center.succeed(ID)
        }
    }

    fun close() = subscription.cancel()

    /**
     * Records a failure of any [kind] (the row is not tied to sync). Failures with an identical [message] and kind share
     * one row, which is refreshed and made unread again.
     */
    fun reportFailure(kind: ActivityKind, message: String) {
        val id = "failed:${kind.name}:${message.hashCode()}"
        center.start(id, kind, message)
        center.fail(id, message)
    }

    private companion object {
        const val ID = "sync"
    }
}
