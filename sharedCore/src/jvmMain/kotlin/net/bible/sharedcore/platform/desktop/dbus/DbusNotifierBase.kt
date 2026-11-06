package net.bible.sharedcore.platform.desktop.dbus

import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.platform.NativeNotifier
import org.freedesktop.dbus.DBusMatchRule
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.exceptions.DBusExecutionException
import org.freedesktop.dbus.messages.DBusSignal

/**
 * Shared plumbing of the D-Bus notifiers (#4012): one long-lived connection, opened lazily on the first operation, and
 * every D-Bus call made on a single-thread IO-limited dispatcher so calls stay ordered and never run on the UI thread.
 *
 * Failure policy: a failed operation logs one warning (re-armed by the next success) and is kept as the *pending*
 * operation of its notification id; the latest operation per id wins (a later show or remove replaces an earlier one).
 * Pending operations are replayed when the retry window [retryAfterFailureMs] ends: one retry is scheduled per outage,
 * up to [MAX_REPLAY_ATTEMPTS] rounds, so nothing is polled per progress tick and a final FAILED notification still
 * arrives once the service is back. The connection is dropped (and server-side state forgotten) only when it was lost
 * or the service is missing; for any other failure (timeout, method error) the connection and the id map are kept.
 * Opening a bus connection is costly, so no connect is attempted inside the window. A missing notification service
 * never throws into the app.
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
    private var retryJob: Job? = null
    private var replayRounds = 0
    private val pending = LinkedHashMap<String, (DBusConnection) -> Unit>()

    private class ClosedWhileConnecting : RuntimeException()

    override fun setOnActivated(handler: (id: String) -> Unit) { this.handler = handler }

    /** Called on the dispatcher after a fresh connection was opened: register signal handlers, probe the server. */
    protected abstract fun onConnected(connection: DBusConnection)

    /** Called on the dispatcher when the connection was dropped; forget server-side state. */
    protected open fun onDisconnected() {}

    /**
     * Queues [block] (with a live connection) on the notifier dispatcher. [key] is the notification id for operations
     * that must survive an outage (latest per key is replayed), or null for fire-and-forget housekeeping.
     */
    protected fun submit(key: String? = null, block: (DBusConnection) -> Unit) {
        if (closed) return
        scope.launch(dispatcher) { run(key, block) }
    }

    private fun run(key: String?, block: (DBusConnection) -> Unit) {
        if (closed) return
        if (key != null) pending.remove(key)
        if (System.nanoTime() - retryNotBeforeNanos < 0) {
            park(key, block)
            return
        }
        val batch = pending.entries.map { it.key to it.value } + (key to block)
        pending.clear()
        runBatch(batch)
    }

    /** Runs [batch] in order; on the first failure it and the rest are parked for the retry. */
    private fun runBatch(batch: List<Pair<String?, (DBusConnection) -> Unit>>) {
        for ((index, entry) in batch.withIndex()) {
            if (!execute(entry.second)) {
                batch.drop(index).forEach { (key, op) -> if (key != null && key !in pending) pending[key] = op }
                scheduleRetry()
                return
            }
        }
        replayRounds = 0
    }

    private fun park(key: String?, block: (DBusConnection) -> Unit) {
        if (key != null) pending[key] = block
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (pending.isEmpty() || retryJob != null) return
        if (replayRounds >= MAX_REPLAY_ATTEMPTS) {
            Log.d(tag, "Dropping ${pending.size} pending notification operation(s) after $MAX_REPLAY_ATTEMPTS replay rounds")
            pending.clear()
            return
        }
        replayRounds++
        retryJob = scope.launch(dispatcher) {
            val waitMs = (retryNotBeforeNanos - System.nanoTime()) / 1_000_000
            if (waitMs > 0) delay(waitMs)
            retryJob = null
            if (closed) return@launch
            val batch = pending.entries.map { it.key to it.value }
            pending.clear()
            if (batch.isNotEmpty()) runBatch(batch)
        }
    }

    /** Runs one operation; false (after applying the failure policy) when it failed. */
    private fun execute(block: (DBusConnection) -> Unit): Boolean {
        var connection = connectionRef.get()
        var connectStage = connection == null
        try {
            if (connection == null) {
                connection = dbus.connect()
                connectionRef.set(connection)
                if (closed) {
                    connectionRef.getAndSet(null)?.let { runCatching { it.close() } }
                    throw ClosedWhileConnecting()
                }
                onConnected(connection)
            }
            connectStage = false
            block(connection)
            warned = false
            return true
        } catch (_: ClosedWhileConnecting) {
            return false
        } catch (e: Exception) {
            if (connectStage || connectionLost(connection, e)) dropConnection()
            retryNotBeforeNanos = System.nanoTime() + retryAfterFailureMs * 1_000_000
            if (!warned) {
                warned = true
                Log.w(tag, "Desktop notification service unavailable, notifications are skipped", e)
            }
            return false
        }
    }

    private fun connectionLost(connection: DBusConnection?, e: Exception): Boolean {
        if (connection == null || !connection.isConnected) return true
        val type = (e as? DBusExecutionException)?.type.orEmpty()
        return LOST_ERRORS.any { type.contains(it) } || e is org.freedesktop.dbus.exceptions.NotConnected
    }

    private fun dropConnection() {
        connectionRef.getAndSet(null)?.let { runCatching { it.close() } }
        runCatching { onDisconnected() }
    }

    /** Invokes the activation handler off the D-Bus dispatch thread. */
    protected fun activated(id: String) {
        handler?.let { h -> scope.launch(Dispatchers.Default) { runCatching { h(id) } } }
    }

    protected fun listenTo(connection: DBusConnection, iface: String, member: String, path: String? = null, arg0: String? = null, sender: String? = null, callback: (DBusSignal) -> Unit) {
        val rule = object : DBusMatchRule("signal", iface, member, path) {
            override fun toString(): String = super.toString() + (arg0?.let { ",arg0='$it'" } ?: "") + (sender?.let { ",sender='$it'" } ?: "")
        }
        connection.addGenericSigHandler(rule) { signal ->
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
     * first, bounded by [FLUSH_TIMEOUT_MS]; if they do not finish, they are abandoned and the connection is closed anyway
     * (a connection opened concurrently notices [closed] and closes itself). Pending retries are discarded.
     */
    override fun close() {
        if (closed) return
        flush()
        closed = true
        connectionRef.getAndSet(null)?.let { runCatching { it.close() } }
    }

    companion object {
        const val DEFAULT_RETRY_AFTER_FAILURE_MS = 5_000L
        const val FLUSH_TIMEOUT_MS = 250L
        const val MAX_REPLAY_ATTEMPTS = 10
        private val LOST_ERRORS = listOf("ServiceUnknown", "NameHasNoOwner", "Disconnected", "NoServer")
    }
}
