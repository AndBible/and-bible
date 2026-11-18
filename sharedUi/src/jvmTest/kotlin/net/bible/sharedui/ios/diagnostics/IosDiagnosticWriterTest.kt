package net.bible.sharedui.ios.diagnostics

import java.nio.file.Files
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedui.host.diagnostics.DiagnosticRecord
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosDiagnosticWriterTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun queuedRecordsAreCoalescedAndBusySnapshotMeetsOneSecondDeadline() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val store = TimedStore(testScheduler)
        val writer = IosDiagnosticWriter("run", { testScheduler.currentTime }, dispatcher, { store }, backgroundScope)
        repeat(256) { writer.emit(RuntimeDiagnosticEvent.ClientReady("before")) }
        val snapshot = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
        writer.emit(RuntimeDiagnosticEvent.ClientReady("overflow"))
        runCurrent()
        val result = snapshot.await()
        assertFalse(result == IosDiagnosticWriter.UNAVAILABLE, "backlog missed snapshot deadline")
        assertEquals(256, result.lines().count { it.isNotEmpty() })
        assertTrue(testScheduler.currentTime < 1000, "queued disk work exceeded the snapshot budget")
        assertTrue(store.batches <= 2, "queued records caused ${store.batches} store writes")
        assertTrue(writer.snapshot().contains("EventsDropped"))
    }

    @Test fun snapshotBarrierKeepsLaterBatchAndLossMarkerInSequenceOrder() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val lines = mutableListOf<DiagnosticRecord>()
        lateinit var writer: IosDiagnosticWriter
        var firstBatch = true
        val store = object : DiagnosticWriterStore {
            override fun append(record: DiagnosticRecord) { lines.add(record) }
            override fun appendBatch(records: List<DiagnosticRecord>) {
                if (firstBatch) {
                    firstBatch = false
                    repeat(256) { writer.emit(RuntimeDiagnosticEvent.ClientReady("late")) }
                    repeat(100) { writer.emit(RuntimeDiagnosticEvent.ClientReady("lost-later")) }
                }
                lines.addAll(records)
            }
            override fun snapshot() = lines.joinToString("") { it.encode() + "\n" }
            override fun takeOmittedCount() = 0L
        }
        writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            store
        }, scope)
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(300) { writer.emit(RuntimeDiagnosticEvent.ClientReady("before")) }
            val snapshot = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
            release.countDown()
            val first = snapshot.await()
            assertFalse(first.contains("late"))
            assertTrue(first.contains("\"count\":44"))
            val all = writer.snapshot()
            assertTrue(all.contains("late") && all.contains("\"count\":100"))
            // The store deliberately does not sort: this checks actual commit order across the barrier.
            val sequences = all.lineSequence().filter { it.isNotEmpty() }.map {
                kotlinx.serialization.json.Json.parseToJsonElement(it).toString()
                    .substringAfter("\"sequence\":").substringBefore(',').toLong()
            }.toList()
            assertEquals((1L..256L).toList() + listOf(301L) + (302L..557L).toList() + listOf(658L), sequences)
        } finally {
            release.countDown()
            withTimeout(5000) { scope.coroutineContext.job.cancelAndJoin() }
        }
    }

    @Test fun batchCompactionLossIsIncludedInSnapshotMarker() = runTest {
        val dir = Files.createTempDirectory("diagnostics-writer-loss").toOkioPath()
        try {
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            val adapter = object : DiagnosticWriterStore {
                override fun append(record: DiagnosticRecord) = store.append(record)
                override fun appendBatch(records: List<DiagnosticRecord>) = store.appendBatch(records)
                override fun snapshot() = store.snapshot()
                override fun takeOmittedCount() = store.takeOmittedCount()
            }
            val dispatcher = StandardTestDispatcher(testScheduler)
            val writer = IosDiagnosticWriter("run", { 1000L }, dispatcher, { adapter }, backgroundScope)
            repeat(256) { writer.emit(RuntimeDiagnosticEvent.ClientReady("p".repeat(3000))) }
            val snapshot = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
            val result = snapshot.await()
            val events = result.lineSequence().filter { it.isNotEmpty() }.map {
                Json.parseToJsonElement(it) as JsonObject
            }.map { it.getValue("event") as JsonObject }.toList()
            val marker = events.single { it["type"].toString() == "\"EventsDropped\"" }
            assertEquals(256 - events.count { it["type"].toString() == "\"ClientReady\"" }, marker.getValue("count").toString().toInt())
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private class TimedStore(private val scheduler: TestCoroutineScheduler) : DiagnosticWriterStore {
        private val records = mutableListOf<DiagnosticRecord>()
        var batches = 0
        override fun append(record: DiagnosticRecord) = appendBatch(listOf(record))
        override fun appendBatch(records: List<DiagnosticRecord>) {
            // Charge synchronous disk work to virtual time: per-record I/O exceeds the 1 s budget.
            scheduler.advanceTimeBy(6)
            batches++
            this.records.addAll(records)
        }
        override fun snapshot() = records.joinToString("") { it.encode() + "\n" }
        override fun takeOmittedCount() = 0L
    }
}
