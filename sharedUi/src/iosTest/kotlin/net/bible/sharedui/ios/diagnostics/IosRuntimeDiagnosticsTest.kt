@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.diagnostics

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import net.bible.sharedui.host.diagnostics.DiagnosticRecord
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUUID
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import platform.darwin.dispatch_time
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosRuntimeDiagnosticsTest {
    @Test
    fun blockedAppendDoesNotBlockEmit() = runBlocking {
        val gate = BlockingGate()
        val store = object : MemoryStore() {
            override fun append(record: DiagnosticRecord) {
                gate.block()
                super.append(record)
            }
        }
        withWriter(storeFactory = { store }) { writer ->
            try {
                writer.emit(RuntimeDiagnosticEvent.ClientReady("first"))
                gate.awaitEntered()
                val producer = async(Dispatchers.Default) {
                    repeat(300) { writer.emit(RuntimeDiagnosticEvent.ClientReady("pane")) }
                }
                withTimeout(500) { producer.await() }
                assertEquals(IosDiagnosticWriter.UNAVAILABLE, withTimeout(1500) { writer.snapshot() })
            } finally {
                gate.release()
            }
        }
    }

    @Test
    fun concurrentProducersHaveUniqueSequence() = runBlocking {
        withWriter { writer ->
            (0 until 8).map { async(Dispatchers.Default) {
                repeat(20) { writer.emit(RuntimeDiagnosticEvent.ClientReady("pane")) }
            } }.awaitAll()
            val sequences = sequences(writer.snapshot())
            assertEquals(160, sequences.distinct().size)
            assertEquals(sequences.sorted(), sequences)
        }
    }

    @Test
    fun unwritableStoreDoesNotEscapeFacade() = runBlocking {
        withWriter(storeFactory = { error("disk") }) { writer ->
            writer.emit(RuntimeDiagnosticEvent.ClientReady("pane"))
            assertEquals(IosDiagnosticWriter.UNAVAILABLE, writer.snapshot())
        }
    }

    @Test
    fun failingStoreDoesNotRecursivelyEmitDrops() = runBlocking {
        val attempts = atomic(0)
        val store = object : DiagnosticWriterStore {
            override fun append(record: DiagnosticRecord) { attempts.incrementAndGet(); error("disk") }
            override fun snapshot() = "ok"
            override fun takeOmittedCount() = 0L
        }
        val initialization = BlockingGate()
        withWriter(storeFactory = { initialization.block(); store }) { writer ->
            try {
                initialization.awaitEntered()
                writer.emit(RuntimeDiagnosticEvent.ClientReady("pane"))
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
                initialization.release()
                assertEquals(IosDiagnosticWriter.UNAVAILABLE, waiting.await())
                assertEquals("ok", writer.snapshot())
                assertEquals(2, attempts.value)
                repeat(3) { assertEquals("ok", writer.snapshot()) }
                assertEquals(2, attempts.value)
            } finally {
                initialization.release()
            }
        }
    }

    @Test
    fun blockedSnapshotTimesOutWithoutBlockingEmitOrAccumulatingRequests() = runBlocking {
        val gate = BlockingGate()
        val reads = atomic(0)
        val store = object : MemoryStore() {
            override fun snapshot(): String {
                if (reads.incrementAndGet() == 1) gate.block()
                return super.snapshot()
            }
        }
        withWriter(storeFactory = { store }) { writer ->
            try {
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
                gate.awaitEntered()
                val producer = async(Dispatchers.Default) { writer.emit(RuntimeDiagnosticEvent.ClientReady("after")) }
                withTimeout(500) { producer.await() }
                assertEquals(IosDiagnosticWriter.UNAVAILABLE, withTimeout(1500) { waiting.await() })
                val requests = (0 until 32).map { async { writer.snapshot() } }
                withTimeout(500) { requests.awaitAll() }.forEach {
                    assertEquals(IosDiagnosticWriter.UNAVAILABLE, it)
                }
                assertEquals(1, reads.value)
            } finally {
                gate.release()
            }
            // The writer's next append proves that the timed-out control request has finished.
            withTimeout(2000) { store.appended.await() }
            assertTrue(writer.snapshot().contains("after"))
            assertEquals(2, reads.value)
        }
    }

    @Test
    fun cancellingSnapshotDoesNotStopWriter() = runBlocking {
        val gate = BlockingGate()
        val store = object : MemoryStore() {
            override fun snapshot(): String {
                gate.block()
                return super.snapshot()
            }
        }
        withWriter(storeFactory = { store }) { writer ->
            try {
                val cancelled = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
                gate.awaitEntered()
                withTimeout(500) { cancelled.cancelAndJoin() }
                writer.emit(RuntimeDiagnosticEvent.ClientReady("after"))
            } finally {
                gate.release()
            }
            withTimeout(2000) { store.appended.await() }
            assertTrue(writer.snapshot().contains("after"))
        }
    }

    @Test
    fun firstSnapshotIncludesPreBarrierDropsAndPreservesLateAdmissionOrder() = runBlocking {
        val initialization = BlockingGate()
        val append = BlockingGate()
        val store = object : MemoryStore() {
            override fun append(record: DiagnosticRecord) {
                if (record.sequence == 1L) append.block()
                super.append(record)
            }
        }
        withWriter(storeFactory = { initialization.block(); store }) { writer ->
            try {
                initialization.awaitEntered()
                repeat(300) { writer.emit(RuntimeDiagnosticEvent.ClientReady("before")) }
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
                initialization.release()
                append.awaitEntered()
                // One slot is now free: this accepted record must stay AFTER the reserved marker.
                writer.emit(RuntimeDiagnosticEvent.ClientReady("late"))
                repeat(100) { writer.emit(RuntimeDiagnosticEvent.ClientReady("post-barrier-loss")) }
                append.release()
                val first = withTimeout(2000) { waiting.await() }
                assertEquals(listOf(44L), dropCounts(first))
                assertFalse(first.contains("late"))
                val all = writer.snapshot()
                assertEquals(listOf(44L, 100L), dropCounts(all))
                assertTrue(all.contains("late"))
                val sequences = sequences(all)
                assertEquals(sequences.sorted(), sequences)
                assertEquals(sequences.size, sequences.distinct().size)
                // Check real append order, not RuntimeDiagnosticStore's sorted export.
                assertEquals((1L..256L).toList() + listOf(301L, 302L, 403L), sequences)
            } finally {
                initialization.release()
                append.release()
            }
        }
    }

    @Test
    fun overflowKeepsAtMost256PendingEncodedRecords() = runBlocking {
        val gate = BlockingGate()
        val store = MemoryStore()
        withWriter(storeFactory = { gate.block(); store }) { writer ->
            try {
                gate.awaitEntered()
                repeat(300) { writer.emit(RuntimeDiagnosticEvent.ClientReady("pane")) }
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
                gate.release()
                val first = withTimeout(2000) { waiting.await() }
                assertEquals(257, sequences(first).size)
                assertEquals(listOf(44L), dropCounts(first))
                assertEquals(first, writer.snapshot())
            } finally {
                gate.release()
            }
        }
    }

    @Test
    fun preBarrierStoreOmissionsAreIncludedWithoutRecursiveMarkers() = runBlocking {
        val gate = BlockingGate()
        val store = object : MemoryStore() {
            private var omitted = 0L
            override fun append(record: DiagnosticRecord) {
                if (record.sequence == 1L) {
                    gate.block()
                    omitted = 1L
                } else super.append(record)
            }
            override fun takeOmittedCount() = omitted.also { omitted = 0L }
        }
        withWriter(storeFactory = { store }) { writer ->
            try {
                writer.emit(RuntimeDiagnosticEvent.ClientReady("omitted"))
                gate.awaitEntered()
                val waiting = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
                gate.release()
                assertEquals(listOf(1L), dropCounts(waiting.await()))
                assertEquals(listOf(1L), dropCounts(writer.snapshot()))
            } finally {
                gate.release()
            }
        }
    }

    @Test
    fun startupPreservesEarlierRun() = runBlocking {
        val directory = NSTemporaryDirectory().toPath() / ("diagnostics-test-" + NSUUID().UUIDString)
        try {
            withWriter("first", { diagnosticStore(directory) { 1L } }) { first ->
                first.emit(RuntimeDiagnosticEvent.ClientReady("one"))
                assertTrue(first.snapshot().contains("first"))
            }
            withWriter("second", { diagnosticStore(directory) { 1L } }) { second ->
                second.emit(RuntimeDiagnosticEvent.ClientReady("two"))
                val snapshot = second.snapshot()
                assertTrue(snapshot.contains("first"))
                assertTrue(snapshot.contains("second"))
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    @Test
    fun backupExcludedForDirectoryAndBothRewrittenFiles() = runBlocking {
        val directory = NSTemporaryDirectory().toPath() / ("diagnostics-test-" + NSUUID().UUIDString)
        try {
            withWriter(storeFactory = { diagnosticStore(directory) { 1L } }) { writer ->
                // Four alternating generations create and then rewrite each slot.
                repeat(4) {
                    writer.emit(RuntimeDiagnosticEvent.ClientReady("pane"))
                    assertTrue(writer.snapshot().contains("pane"))
                }
                listOf(directory, directory / "diagnostics.0.ndjson", directory / "diagnostics.1.ndjson").forEach { path ->
                    assertTrue(FileSystem.SYSTEM.exists(path))
                    val values = NSURL.fileURLWithPath(path.toString()).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
                    assertEquals(true, (values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue)
                }
            }
        } finally {
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    private suspend fun withWriter(
        runId: String = "run",
        storeFactory: () -> DiagnosticWriterStore = { MemoryStore() },
        block: suspend CoroutineScope.(IosDiagnosticWriter) -> Unit,
    ) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val writer = IosDiagnosticWriter(runId, { 1L }, Dispatchers.Default, storeFactory, scope = scope)
        try { scope.block(writer) }
        finally { withTimeout(2000) { scope.coroutineContext.job.cancelAndJoin() } }
    }

    private fun sequences(snapshot: String) = records(snapshot).map { it.getValue("sequence").jsonPrimitive.long }
    private fun dropCounts(snapshot: String) = records(snapshot).filter {
        it.getValue("event").jsonObject.getValue("type").jsonPrimitive.content == "EventsDropped"
    }.map { it.getValue("event").jsonObject.getValue("count").jsonPrimitive.long }
    private fun records(snapshot: String) = snapshot.lineSequence().filter { it.isNotEmpty() }.map {
        Json.parseToJsonElement(it).jsonObject
    }.toList()

    private class BlockingGate {
        private val entered = CompletableDeferred<Unit>()
        private val semaphore = dispatch_semaphore_create(0)!!
        private val released = atomic(false)
        fun block() {
            if (released.value) return
            entered.complete(Unit)
            check(dispatch_semaphore_wait(semaphore, dispatch_time(DISPATCH_TIME_NOW, 10_000_000_000L)) == 0L)
        }
        suspend fun awaitEntered() { withTimeout(2000) { entered.await() } }
        fun release() { if (released.compareAndSet(false, true)) dispatch_semaphore_signal(semaphore) }
    }

    private open class MemoryStore : DiagnosticWriterStore {
        private val lines = mutableListOf<String>()
        val appended = CompletableDeferred<Unit>()
        override fun append(record: DiagnosticRecord) { lines.add(record.encode()); appended.complete(Unit) }
        override fun snapshot(): String = lines.joinToString("", transform = { "$it\n" })
        override fun takeOmittedCount(): Long = 0L
    }
}
