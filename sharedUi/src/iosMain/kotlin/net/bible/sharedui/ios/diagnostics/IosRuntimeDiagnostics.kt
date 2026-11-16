@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.diagnostics

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import net.bible.sharedui.host.diagnostics.DiagnosticRecord
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticStore
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import net.bible.sharedcore.log.IosLogSink
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970

internal interface DiagnosticWriterStore {
    fun append(record: DiagnosticRecord)
    fun snapshot(): String
    fun takeOmittedCount(): Long
}

internal fun diagnosticStore(directory: Path, clock: () -> Long): DiagnosticWriterStore {
    FileSystem.SYSTEM.createDirectories(directory)
    excludeFromBackup(directory)
    val store = RuntimeDiagnosticStore(directory, FileSystem.SYSTEM, clock)
    return object : DiagnosticWriterStore {
        override fun append(record: DiagnosticRecord) {
            store.append(record)
            listOf("diagnostics.0.ndjson", "diagnostics.1.ndjson").forEach { name ->
                val path = directory / name
                if (FileSystem.SYSTEM.exists(path)) excludeFromBackup(path)
            }
        }
        override fun snapshot() = store.snapshot()
        override fun takeOmittedCount() = store.takeOmittedCount()
    }
}

private fun excludeFromBackup(path: Path) {
    try { NSURL.fileURLWithPath(path.toString()).setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null) }
    catch (_: Exception) { }
}

internal object IosRuntimeDiagnostics {
    private val clock: () -> Long = { (NSDate().timeIntervalSince1970 * 1000).toLong() }
    private val writer by lazy {
        IosDiagnosticWriter(NSUUID().UUIDString, clock, Dispatchers.Default, {
            val root = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
            diagnosticStore(root.toPath() / "AndBible" / "Diagnostics", clock)
        }) { IosLogSink.current()?.invoke(it) }
    }

    private val installed = atomic(false)

    fun install() {
        if (!installed.compareAndSet(false, true)) return
        val bundle = NSBundle.mainBundle
        fun value(key: String) = (bundle.objectForInfoDictionaryKey(key) as? String ?: "").take(100)
        emit(RuntimeDiagnosticEvent.RunStarted(value("CFBundleShortVersionString"), value("CFBundleVersion"), value("AndBibleCommit")))
    }

    fun emit(event: RuntimeDiagnosticEvent) = writer.emit(event)
    suspend fun snapshot(): String = writer.snapshot()
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
        class Snapshot(val sequence: Long, val time: Long, val dropped: Long) : Request {
            val result = CompletableDeferred<String>()
        }
    }

    private val queue = ArrayDeque<Request.Append>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = NSLock()
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
                    else queue.removeFirstOrNull()
                }
                when (request) {
                    null -> wake.receive()
                    is Request.Append -> {
                        try { mirror(request.encoded) } catch (_: Exception) { }
                        if (store != null) {
                            val omitted = try {
                                store.append(request.record)
                                store.takeOmittedCount()
                            } catch (_: Exception) { 1L }
                            // A failed loss indication must never generate another indication.
                            if (request.record.event !is RuntimeDiagnosticEvent.EventsDropped) {
                                writerDropped = saturatedAdd(writerDropped, omitted)
                            }
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
        lock.lock()
        try { return block() } finally { lock.unlock() }
    }

    private fun saturatedAdd(current: Long, count: Long): Long =
        if (count > Long.MAX_VALUE - current) Long.MAX_VALUE else current + count

    companion object { const val UNAVAILABLE = "diagnostics unavailable\n" }
}
