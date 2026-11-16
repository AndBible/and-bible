@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package net.bible.sharedui.ios.diagnostics

import kotlinx.coroutines.Dispatchers
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSNumber
import net.bible.sharedui.host.diagnostics.DiagnosticRecord
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import platform.darwin.DISPATCH_TIME_FOREVER
import platform.darwin.dispatch_semaphore_create
import platform.darwin.dispatch_semaphore_signal
import platform.darwin.dispatch_semaphore_wait
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosRuntimeDiagnosticsTest {
    @Test
    fun blockedWriterDoesNotBlockEmit() = runBlocking {
        val gate = dispatch_semaphore_create(0)!!
        val entered = dispatch_semaphore_create(0)!!
        val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, {
            dispatch_semaphore_signal(entered)
            dispatch_semaphore_wait(gate, DISPATCH_TIME_FOREVER)
            MemoryStore()
        })
        try {
            withContext(Dispatchers.Default) { dispatch_semaphore_wait(entered, DISPATCH_TIME_FOREVER) }
            repeat(300) { writer.emit(RuntimeDiagnosticEvent.ClientReady("pane")) }
            assertTrue(writer.snapshot().contains("unavailable"))
        } finally {
            dispatch_semaphore_signal(gate)
        }
    }

    @Test
    fun concurrentProducersHaveUniqueSequence() = runBlocking {
        val store = MemoryStore()
        val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, { store })
        (0 until 8).map { async(Dispatchers.Default) {
            repeat(20) { writer.emit(RuntimeDiagnosticEvent.ClientReady("pane")) }
        } }.awaitAll()
        val lines = writer.snapshot().lines().filter { it.isNotEmpty() }
        val sequences = lines.map { kotlinx.serialization.json.Json.parseToJsonElement(it).jsonObject.getValue("sequence").jsonPrimitive.long }
        assertEquals(160, sequences.distinct().size)
        assertEquals(sequences.sorted(), sequences)
    }

    @Test
    fun unwritableStoreDoesNotEscapeFacade() = runBlocking {
        val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, { error("disk") })
        writer.emit(RuntimeDiagnosticEvent.ClientReady("pane"))
        assertTrue(writer.snapshot().contains("unavailable"))
    }

    @Test
    fun failingStoreDoesNotRecursivelyEmitDrops() = runBlocking {
        val attempts = atomic(0)
        val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, {
            object : DiagnosticWriterStore {
                override fun append(record: DiagnosticRecord) { attempts.incrementAndGet(); error("disk") }
                override fun snapshot() = "ok"
                override fun takeOmittedCount() = 0L
            }
        })
        writer.emit(RuntimeDiagnosticEvent.ClientReady("pane"))
        assertEquals("ok", withTimeout(2000) { writer.snapshot() })
        assertEquals("ok", withTimeout(2000) { writer.snapshot() })
        assertEquals(2, attempts.value)
        writer.emit(RuntimeDiagnosticEvent.ClientReady("later"))
        assertEquals("ok", withTimeout(2000) { writer.snapshot() })
        assertTrue(attempts.value <= 4)
    }

    @Test
    fun snapshotTimesOutAndCancellationDoesNotStopWriter() = runBlocking {
        val gate = dispatch_semaphore_create(0)!!
        val entered = dispatch_semaphore_create(0)!!
        val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, {
            dispatch_semaphore_signal(entered)
            dispatch_semaphore_wait(gate, DISPATCH_TIME_FOREVER)
            MemoryStore()
        })
        try {
            withContext(Dispatchers.Default) { dispatch_semaphore_wait(entered, DISPATCH_TIME_FOREVER) }
            val cancelled = launch { writer.snapshot() }
            delay(50)
            cancelled.cancelAndJoin()
            assertEquals(IosDiagnosticWriter.UNAVAILABLE, withTimeout(2000) { writer.snapshot() })
        } finally {
            dispatch_semaphore_signal(gate)
        }
        writer.emit(RuntimeDiagnosticEvent.ClientReady("after"))
        assertTrue(writer.snapshot().contains("after"))
    }

    @Test
    fun overflowCoalescesDroppedCount() = runBlocking {
        val gate = dispatch_semaphore_create(0)!!
        val entered = dispatch_semaphore_create(0)!!
        val store = MemoryStore()
        val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, {
            dispatch_semaphore_signal(entered)
            dispatch_semaphore_wait(gate, DISPATCH_TIME_FOREVER)
            store
        })
        try {
            withContext(Dispatchers.Default) { dispatch_semaphore_wait(entered, DISPATCH_TIME_FOREVER) }
            repeat(300) { writer.emit(RuntimeDiagnosticEvent.ClientReady("pane")) }
        } finally {
            dispatch_semaphore_signal(gate)
        }
        withTimeout(10000) { writer.snapshot() }
        val snapshot = withTimeout(10000) { writer.snapshot() }
        val drops = snapshot.lines().filter { it.contains("EventsDropped") }
        assertEquals(1, drops.size)
        assertTrue(drops.single().contains("\"count\":44"))
    }

    @Test
    fun startupPreservesEarlierRun() = runBlocking {
        val directory = NSTemporaryDirectory().toPath() / ("diagnostics-test-" + NSUUID().UUIDString)
        try {
            val first = IosDiagnosticWriter("first", { 1L }, Dispatchers.Default, { diagnosticStore(directory) { 1L } })
            first.emit(RuntimeDiagnosticEvent.ClientReady("one"))
            assertTrue(first.snapshot().contains("first"))
            val second = IosDiagnosticWriter("second", { 1L }, Dispatchers.Default, { diagnosticStore(directory) { 1L } })
            second.emit(RuntimeDiagnosticEvent.ClientReady("two"))
            val snapshot = second.snapshot()
            assertTrue(snapshot.contains("first"))
            assertTrue(snapshot.contains("second"))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    @Test
    fun backupExcluded() = runBlocking {
        val directory = NSTemporaryDirectory().toPath() / ("diagnostics-test-" + NSUUID().UUIDString)
        try {
            val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, { diagnosticStore(directory) { 1L } })
            writer.emit(RuntimeDiagnosticEvent.ClientReady("pane"))
            writer.snapshot()
            val values = NSURL.fileURLWithPath(directory.toString()).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), null)
            assertEquals(true, (values?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(directory)
        }
    }

    private class MemoryStore : DiagnosticWriterStore {
        private val lines = mutableListOf<String>()
        override fun append(record: DiagnosticRecord) { lines.add(record.encode()) }
        override fun snapshot(): String = lines.joinToString("\n", postfix = "\n")
        override fun takeOmittedCount(): Long = 0
    }
}
