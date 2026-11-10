package net.bible.android.control.activity

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.flow.MutableStateFlow
import net.bible.sharedcore.event.Subscription
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.bible.sharedcore.platform.AppSettings

/**
 * Whether background activity is also reported through native system notifications (#4012).
 *
 * Stored under [KEY], separate from the in-app [ActivityMenuPreference]. Unset means [defaultEnabled]; reading never
 * persists the default. [enabled] does not re-read the store on access: the backing store may answer the default until
 * the settings database is open, so call [refresh] when the databases open (desktop startup). [set] writes through
 * and publishes, and is what the settings row uses.
 *
 * Bound in Koin only on platforms that have a native notifier; elsewhere the settings row is hidden.
 */
class SystemNotificationsPreference(
    private val settings: AppSettings,
    private val defaultEnabled: Boolean,
) {
    private val _enabled = MutableStateFlow(read())

    /** Current value; emits when [set] or [refresh] changes it. */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private class Observer(val callback: (Snapshot) -> Unit) { var cancelled = false }
    private val lock = SynchronizedObject()
    private val observers = mutableListOf<Observer>()
    private val pending = ArrayDeque<() -> Unit>()
    private var draining = false

    /** Accepted state, independent of a delayed observer drainer. Off revision preserves rapid off/on. */
    internal data class Snapshot(val enabled: Boolean, val revision: Long, val lastOffRevision: Long)
    private var accepted = Snapshot(_enabled.value, 0, 0)
    internal fun snapshot(): Snapshot = synchronized(lock) { accepted } // L1-common-ok: atomicfu lock

    /** Replays the current value before ordered changes, without StateFlow conflation. Cancel on owner close. */
    fun observe(callback: (Boolean) -> Unit): Subscription = observeSnapshot { callback(it.enabled) }

    internal fun observeSnapshot(callback: (Snapshot) -> Unit): Subscription {
        val observer = Observer(callback)
        synchronized(lock) { // L1-common-ok: atomicfu lock
            observers += observer
            enqueue(observer, accepted)
        }
        drain()
        return Subscription {
            synchronized(lock) { observer.cancelled = true; observers.remove(observer) } // L1-common-ok: atomicfu lock
        }
    }

    /** Re-reads the store; call once the settings database is ready. */
    fun refresh() {
        synchronized(lock) { publish(read()) } // L1-common-ok: atomicfu lock
        drain()
    }

    /** Stores [enabled] explicitly (it then wins over the default) and publishes every changed edge. */
    fun set(enabled: Boolean) {
        synchronized(lock) { // L1-common-ok: atomicfu lock
            settings.setBoolean(KEY, enabled)
            publish(enabled)
        }
        drain()
    }

    private fun publish(value: Boolean) {
        if (_enabled.value == value) return
        val revision = accepted.revision + 1
        accepted = Snapshot(value, revision, if (value) accepted.lastOffRevision else revision)
        observers.forEach { enqueue(it, accepted) }
        _enabled.value = value
    }

    private fun enqueue(observer: Observer, value: Snapshot) {
        pending.addLast {
            val active = synchronized(lock) { !observer.cancelled } // L1-common-ok: atomicfu lock
            if (active) observer.callback(value)
        }
    }

    // One drainer invokes callbacks outside the lock, including changes accepted reentrantly.
    private fun drain() {
        synchronized(lock) { // L1-common-ok: atomicfu lock
            if (draining) return
            draining = true
        }
        while (true) {
            val next = synchronized(lock) { // L1-common-ok: atomicfu lock
                if (pending.isEmpty()) { draining = false; null } else pending.removeFirst()
            } ?: return
            try { next() } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun read(): Boolean = settings.getBoolean(KEY, defaultEnabled)

    companion object {
        const val KEY = "system_notifications"
    }
}
