package net.bible.sharedui.ios.diagnostics

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedui.host.diagnostics.DiagnosticRecord
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent

internal interface DiagnosticWriterStore {
    fun append(record: DiagnosticRecord)
    fun appendBatch(records: List<DiagnosticRecord>) { records.forEach(::append) }
    fun snapshot(): String
    fun takeOmittedCount(): Long
}

/** A single consumer owns the store; the admission lock is never held during I/O. */
internal class IosDiagnosticWriter(
    private val runId: String,
    private val clock: () -> Long,
    dispatcher: CoroutineDispatcher,
    storeFactory: () -> DiagnosticWriterStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher),
    private val mirror: (String) -> Unit = {},
) {
    private sealed interface Request {
        class Append(val record: DiagnosticRecord, val encoded: String) : Request
        class Batch(val appends: List<Append>) : Request
        class Snapshot(val sequence: Long, val time: Long, val dropped: Long) : Request {
            val result = CompletableDeferred<String>()
        }
    }

    private val queue = ArrayDeque<Request.Append>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = SynchronizedObject()
    private var sequence = 0L
    private var dropped = 0L
    private var barrier: Request.Snapshot? = null

    init {
        scope.launch {
            val store = try { storeFactory() } catch (_: Exception) { null }
            var writerDropped = 0L
            while (isActive) {
                val request = locked {
                    // A marker is admitted at the tail, never ahead of already accepted records.
                    // While a barrier is pending its loss belongs to that reserved sequence instead.
                    if (barrier == null && queue.size < 256 && (dropped > 0 || writerDropped > 0)) {
                        val count = saturatedAdd(dropped, writerDropped)
                        val record = DiagnosticRecord(runId, ++sequence, clock(), RuntimeDiagnosticEvent.EventsDropped(count))
                        queue.addLast(Request.Append(record, record.encode()))
                        dropped = 0L
                        writerDropped = 0L
                    }
                    val control = barrier
                    if (control != null && (queue.isEmpty() || queue.first().record.sequence > control.sequence)) control
                    else if (queue.isEmpty()) null else {
                        val appends = mutableListOf<Request.Append>()
                        while (queue.isNotEmpty() && (control == null || queue.first().record.sequence < control.sequence)) {
                            appends.add(queue.removeFirst())
                        }
                        Request.Batch(appends)
                    }
                }
                when (request) {
                    null -> wake.receive()
                    is Request.Append -> error("append requests must be batched")
                    is Request.Batch -> {
                        request.appends.forEach { try { mirror(it.encoded) } catch (_: Exception) { } }
                        if (store != null) {
                            val reportable = request.appends.count { it.record.event !is RuntimeDiagnosticEvent.EventsDropped }.toLong()
                            val omitted = try {
                                store.appendBatch(request.appends.map { it.record })
                                store.takeOmittedCount().coerceAtMost(reportable)
                            } catch (_: Exception) { reportable }
                            // A failed loss indication must never generate another indication.
                            writerDropped = saturatedAdd(writerDropped, omitted)
                        }
                    }
                    is Request.Snapshot -> {
                        val count = saturatedAdd(request.dropped, writerDropped)
                        writerDropped = 0L
                        val snapshot = try {
                            if (store == null) UNAVAILABLE else {
                                if (count > 0) {
                                    val marker = DiagnosticRecord(runId, request.sequence, request.time, RuntimeDiagnosticEvent.EventsDropped(count))
                                    try { mirror(marker.encode()) } catch (_: Exception) { }
                                    store.append(marker)
                                    // Do not recursively report loss of this marker or claim it persisted.
                                    if (store.takeOmittedCount() > 0) error("diagnostic marker omitted")
                                }
                                store.snapshot()
                            }
                        } catch (_: Exception) { UNAVAILABLE }
                        locked { barrier = null }
                        request.result.complete(snapshot)
                    }
                }
            }
        }
    }

    fun emit(event: RuntimeDiagnosticEvent) {
        locked {
            try {
                val record = DiagnosticRecord(runId, ++sequence, clock(), event)
                val encoded = record.encode()
                if (encoded.length >= 4096 || (encoded + "\n").encodeToByteArray().size > 4096 || queue.size == 256) {
                    dropped = saturatedAdd(dropped, 1L)
                } else queue.addLast(Request.Append(record, encoded))
            } catch (_: Exception) { dropped = saturatedAdd(dropped, 1L) }
        }
        wake.trySend(Unit)
    }

    suspend fun snapshot(): String = withTimeoutOrNull(1000) {
        val request = locked {
            // A full data queue cannot delay control admission. Reserving its sequence now
            // prevents later accepted events from preceding the barrier's loss marker.
            if (barrier != null) null else {
                Request.Snapshot(++sequence, clock(), dropped).also {
                    dropped = 0L
                    barrier = it
                }
            }
        } ?: return@withTimeoutOrNull UNAVAILABLE
        wake.trySend(Unit)
        // Timeout/cancellation leaves this one bounded control slot owned by the writer.
        request.result.await()
    } ?: UNAVAILABLE

    private inline fun <T> locked(block: () -> T): T {
        return synchronized(lock, block)
    }

    private fun saturatedAdd(current: Long, count: Long): Long =
        if (count > Long.MAX_VALUE - current) Long.MAX_VALUE else current + count

    companion object { const val UNAVAILABLE = "diagnostics unavailable\n" }
}
