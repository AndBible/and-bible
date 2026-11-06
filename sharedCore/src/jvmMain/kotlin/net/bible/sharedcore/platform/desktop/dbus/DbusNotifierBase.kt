package net.bible.sharedcore.platform.desktop.dbus

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.NativeNotifier
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.messages.DBusSignal

/**
 * Shared plumbing of the D-Bus notifiers (#4012): one long-lived connection, opened lazily on the first operation, and
 * every D-Bus call made on a single-thread IO-limited dispatcher so calls stay ordered and never run on the UI thread.
 *
 * Failure policy: any failed operation drops the connection and logs one warning; the next operation tries once to
 * reconnect, but not sooner than [retryAfterFailureMs] after the failure (operations in that window are dropped, because
 * opening a bus connection is costly). While failures continue nothing more is logged; a successful operation re-arms
 * the warning. A missing notification service therefore never throws into the app.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class DbusNotifierBase(
    private val dbus: DesktopDbus,
    private val scope: CoroutineScope,
    private val tag: String,
    private val retryAfterFailureMs: Long,
) : NativeNotifier, AutoCloseable {
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)
    private val connectionRef = AtomicReference<DBusConnection?>()

    @Volatile private var closed = false
    @Volatile private var handler: ((String) -> Unit)? = null

    // Confined to [dispatcher].
    private var warned = false
    private var retryNotBeforeNanos = 0L

    override fun setOnActivated(handler: (id: String) -> Unit) { this.handler = handler }

    /** Called on the dispatcher after a fresh connection was opened: register signal handlers, probe the server. */
    protected abstract fun onConnected(connection: DBusConnection)

    /** Called on the dispatcher when the connection was dropped after a failure; forget server-side state. */
    protected open fun onDisconnected() {}

    /** Queues [block] (with a live connection) on the notifier dispatcher; failures are absorbed per the policy above. */
    protected fun submit(block: (DBusConnection) -> Unit) {
        if (closed) return
        scope.launch(dispatcher) { runGuarded(block) }
    }

    private fun runGuarded(block: (DBusConnection) -> Unit) {
        if (closed) return
        if (connectionRef.get() == null && System.nanoTime() - retryNotBeforeNanos < 0) return
        try {
            val connection = connectionRef.get() ?: dbus.connect().also { fresh ->
                connectionRef.set(fresh)
                onConnected(fresh)
            }
            block(connection)
            warned = false
        } catch (e: Exception) {
            dropConnection()
            retryNotBeforeNanos = System.nanoTime() + retryAfterFailureMs * 1_000_000
            if (!warned) {
                warned = true
                Log.w(tag, "Desktop notification service unavailable, notifications are skipped", e)
            }
        }
    }

    private fun dropConnection() {
        connectionRef.getAndSet(null)?.let { runCatching { it.close() } }
        runCatching { onDisconnected() }
    }

    /** Invokes the activation handler off the D-Bus dispatch thread. */
    protected fun activated(id: String) {
        handler?.let { h -> scope.launch(Dispatchers.Default) { runCatching { h(id) } } }
    }

    protected fun listenTo(connection: DBusConnection, iface: String, member: String, callback: (DBusSignal) -> Unit) {
        connection.addGenericSigHandler(org.freedesktop.dbus.DBusMatchRule("signal", iface, member, null)) { signal ->
            runCatching { callback(signal) }
        }
    }

    /** Waits (bounded) until everything queued so far has run; for [close] and tests. */
    fun flush(timeoutMs: Long = FLUSH_TIMEOUT_MS): Boolean {
        val marker: Job = scope.launch(dispatcher) { }
        return runBlocking { withTimeoutOrNull(timeoutMs) { marker.join() } != null }
    }

    /**
     * Closes the connection. Operations queued before the call (typically the final removes at app exit) are flushed
     * first, bounded by [FLUSH_TIMEOUT_MS]; if they do not finish, they are abandoned and the connection is closed anyway.
     */
    override fun close() {
        if (closed) return
        flush()
        closed = true
        connectionRef.getAndSet(null)?.let { runCatching { it.close() } }
    }

    companion object {
        const val DEFAULT_RETRY_AFTER_FAILURE_MS = 5_000L
        const val FLUSH_TIMEOUT_MS = 2_000L
    }
}
