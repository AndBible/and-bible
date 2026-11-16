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
    private val mirror: (String) -> Unit = {},
) {
    private sealed interface Request {
        class Append(val record: DiagnosticRecord, val encoded: String) : Request
        class Snapshot(val result: CompletableDeferred<String>) : Request
    }

    private val queue = Channel<Request>(256)
    private val lock = NSLock()
    private var sequence = 0L
    private val dropped = atomic(0L)
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    init {
        scope.launch {
            val store = try { storeFactory() } catch (_: Exception) { null }
            for (request in queue) {
                when (request) {
                    is Request.Append -> {
                        try { mirror(request.encoded) } catch (_: Exception) { }
                        if (store != null) {
                            try {
                                store.append(request.record)
                                addDropped(store.takeOmittedCount())
                            } catch (_: Exception) {
                                if (request.record.event !is RuntimeDiagnosticEvent.EventsDropped) addDropped(1)
                            }
                        }
                        if (request.record.event !is RuntimeDiagnosticEvent.EventsDropped) enqueueDrops()
                    }
                    is Request.Snapshot -> {
                        enqueueDrops()
                        request.result.complete(
                            try { store?.snapshot() ?: UNAVAILABLE } catch (_: Exception) { UNAVAILABLE }
                        )
                    }
                }
            }
        }
    }

    fun emit(event: RuntimeDiagnosticEvent) {
        lock.lock()
        try {
            val record = DiagnosticRecord(runId, ++sequence, clock(), event)
            val encoded = record.encode()
            if (encoded.length >= 4096 || (encoded + "\n").encodeToByteArray().size > 4096 ||
                queue.trySend(Request.Append(record, encoded)).isFailure) addDropped(1)
        } catch (_: Exception) { addDropped(1) }
        finally { lock.unlock() }
    }

    suspend fun snapshot(): String {
        val result = CompletableDeferred<String>()
        return withTimeoutOrNull(1000) {
            queue.send(Request.Snapshot(result))
            result.await()
        } ?: UNAVAILABLE
    }

    private fun enqueueDrops() {
        lock.lock()
        var count = 0L
        try {
            count = dropped.getAndSet(0)
            if (count == 0L) return
            val record = DiagnosticRecord(runId, ++sequence, clock(), RuntimeDiagnosticEvent.EventsDropped(count))
            if (queue.trySend(Request.Append(record, record.encode())).isFailure) addDropped(count)
        } catch (_: Exception) { addDropped(count) }
        finally { lock.unlock() }
    }

    private fun addDropped(count: Long) {
        while (true) {
            val current = dropped.value
            val next = if (count > Long.MAX_VALUE - current) Long.MAX_VALUE else current + count
            if (dropped.compareAndSet(current, next)) return
        }
    }

    companion object { const val UNAVAILABLE = "diagnostics unavailable\n" }
}
