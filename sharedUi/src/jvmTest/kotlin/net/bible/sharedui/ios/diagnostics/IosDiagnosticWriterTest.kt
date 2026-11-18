package net.bible.sharedui.ios.diagnostics

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
import net.bible.sharedui.host.diagnostics.DiagnosticRecord
import net.bible.sharedui.host.diagnostics.RuntimeDiagnosticEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosDiagnosticWriterTest {
    @Test fun queuedRecordsAreCoalescedAndBusySnapshotMeetsOneSecondDeadline() = runBlocking {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val store = SlowStore()
        val writer = IosDiagnosticWriter("run", { 1L }, Dispatchers.Default, {
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            store
        }, scope)
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            repeat(256) { writer.emit(RuntimeDiagnosticEvent.ClientReady("before")) }
            val snapshot = async(start = CoroutineStart.UNDISPATCHED) { writer.snapshot() }
            writer.emit(RuntimeDiagnosticEvent.ClientReady("overflow"))
            release.countDown()
            val result = withTimeout(1200) { snapshot.await() }
            assertFalse(result == IosDiagnosticWriter.UNAVAILABLE, "backlog missed snapshot deadline")
            assertEquals(256, result.lines().count { it.isNotEmpty() })
            assertTrue(store.batches <= 2, "queued records caused ${store.batches} store writes")
            assertTrue(writer.snapshot().contains("EventsDropped"))
        } finally {
            release.countDown()
            withTimeout(5000) { scope.coroutineContext.job.cancelAndJoin() }
        }
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

    private class SlowStore : DiagnosticWriterStore {
        private val records = mutableListOf<DiagnosticRecord>()
        var batches = 0
        override fun append(record: DiagnosticRecord) = appendBatch(listOf(record))
        override fun appendBatch(records: List<DiagnosticRecord>) {
            Thread.sleep(6) // One bounded disk operation; per-record writes exceed the report deadline.
            batches++
            this.records.addAll(records)
        }
        override fun snapshot() = records.joinToString("") { it.encode() + "\n" }
        override fun takeOmittedCount() = 0L
    }
}
