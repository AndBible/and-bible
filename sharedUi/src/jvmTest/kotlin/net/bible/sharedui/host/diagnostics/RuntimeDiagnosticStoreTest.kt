package net.bible.sharedui.host.diagnostics

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import okio.buffer
import okio.FileSystem
import okio.ForwardingFileSystem
import okio.Path
import okio.Sink
import okio.ForwardingSink
import okio.Buffer
import java.io.IOException
import okio.Path.Companion.toOkioPath

class RuntimeDiagnosticStoreTest {
    @Test fun interruptedOpenKeepsCompletedHistory() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            val base = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            base.append(DiagnosticRecord("first", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            val failing = object : ForwardingFileSystem(FileSystem.SYSTEM) {
                override fun sink(file: Path, mustCreate: Boolean): Sink = throw IOException("injected open interruption")
            }
            try {
                RuntimeDiagnosticStore(dir, failing) { 1000L }.append(DiagnosticRecord("second", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            } catch (_: IOException) { }
            assertTrue(RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot().contains("first"))
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun interruptedSnapshotWritesKeepCompletedHistory() {
        for (cutoff in listOf(0L, 1L, 32L, 128L, 1024L)) {
            val dir = Files.createTempDirectory("diagnostics").toOkioPath()
            try {
                val base = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
                base.append(DiagnosticRecord("first", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
                base.append(DiagnosticRecord("second", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
                val failing = object : ForwardingFileSystem(FileSystem.SYSTEM) {
                    override fun sink(file: Path, mustCreate: Boolean): Sink {
                        val sink = super.sink(file, mustCreate)
                        return object : ForwardingSink(sink) {
                            var left = cutoff
                            override fun write(source: Buffer, byteCount: Long) {
                                val count = minOf(left, byteCount)
                                if (count > 0) { super.write(source, count); left -= count }
                                if (count != byteCount) throw IOException("injected interruption")
                            }
                            override fun close() { super.close(); throw IOException("injected close interruption") }
                        }
                    }
                }
                try {
                    RuntimeDiagnosticStore(dir, failing) { 1000L }.append(DiagnosticRecord("third", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
                } catch (_: IOException) { }
                val result = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot()
                assertTrue(result.contains("first"), "cutoff=$cutoff $result")
                assertTrue(result.contains("second"), "cutoff=$cutoff $result")
            } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
        }
    }

    @Test fun oversizedRecordIsNotWritten() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            store.append(DiagnosticRecord("x".repeat(5000), 0, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            assertTrue(store.takeOmittedCount() == 1L)
            assertTrue(store.takeOmittedCount() == 0L)
            store.append(DiagnosticRecord("valid", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            val result = store.snapshot()
            assertTrue(!result.contains("x".repeat(5000)), result)
            assertTrue(result.contains("valid"), result)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @Test fun sizeLimitsHoldWithFutureTimestampsAndUtf8() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            val fs = FileSystem.SYSTEM
            val store = RuntimeDiagnosticStore(dir, fs) { 1L }
            repeat(180) { n ->
                store.append(DiagnosticRecord("ä".repeat(1900), n.toLong(), Long.MAX_VALUE, RuntimeDiagnosticEvent.ClientReady("pane")))
            }
            val files = fs.list(dir)
            assertTrue(files.size <= 2)
            assertTrue(files.all { fs.metadata(it).size!! <= 524288L })
            assertTrue(files.sumOf { fs.metadata(it).size!! } <= 1048576L)
            assertTrue(store.snapshot().contains("\"sequence\":179"))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @Test fun recordsAreOrderedBySequenceWithinRun() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            store.append(DiagnosticRecord("same", 2, 900, RuntimeDiagnosticEvent.ClientReady("pane")))
            store.append(DiagnosticRecord("same", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            val result = store.snapshot()
            assertTrue(result.indexOf("\"sequence\":1") < result.indexOf("\"sequence\":2"), result)
            assertTrue(result.contains("\"sequence\":1") && result.contains("\"sequence\":2"), result)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @Test fun partialTailDoesNotDestroyCompletedRecord() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            val fs = FileSystem.SYSTEM
            val store = RuntimeDiagnosticStore(dir, fs) { 1000L }
            store.append(DiagnosticRecord("complete", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            fs.list(dir).forEach { file -> fs.appendingSink(file).buffer().use { it.writeUtf8("{broken") } }
            val reopened = RuntimeDiagnosticStore(dir, fs) { 1000L }
            reopened.append(DiagnosticRecord("next", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            val result = reopened.snapshot()
            assertTrue(result.contains("complete"), result)
            assertTrue(result.contains("next"), result)
            assertTrue(!result.contains("broken"), result)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @Test fun retentionKeepsBoundaryAndFutureButDropsOlderRecords() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            var now = 1_000_000_000L
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { now }
            store.append(DiagnosticRecord("old", 0, now, RuntimeDiagnosticEvent.ClientReady("pane")))
            store.append(DiagnosticRecord("boundary", 0, now + 1, RuntimeDiagnosticEvent.ClientReady("pane")))
            store.append(DiagnosticRecord("future", 0, now + 900_000_000L, RuntimeDiagnosticEvent.ClientReady("pane")))
            now += 7L * 24 * 60 * 60 * 1000 + 1
            val result = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { now }.snapshot()
            assertTrue(!result.contains("old"), result)
            assertTrue(result.contains("boundary"), result)
            assertTrue(result.contains("future"), result)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }

    @Test fun reopenKeepsPreviousRun() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            val first = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            first.append(DiagnosticRecord("run-a", 0, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            val second = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            second.append(DiagnosticRecord("run-b", 0, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            val result = second.snapshot()
            assertTrue(result.contains("run-a"), result)
            assertTrue(result.contains("run-b"), result)
            assertTrue(result.indexOf("run-a") < result.indexOf("run-b"), result)
        } finally {
            FileSystem.SYSTEM.deleteRecursively(dir)
        }
    }
}
