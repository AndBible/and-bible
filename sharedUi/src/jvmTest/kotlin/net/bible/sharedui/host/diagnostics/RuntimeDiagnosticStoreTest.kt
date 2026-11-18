package net.bible.sharedui.host.diagnostics

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
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
    @Test fun fullStoreAppendsHaveBoundedIoInsteadOfRewritingEveryEvent() {
        val dir = Files.createTempDirectory("diagnostics-cost").toOkioPath()
        try {
            val fs = CountingFileSystem()
            val store = RuntimeDiagnosticStore(dir, fs) { 1000L }
            var sequence = 0L
            while (FileSystem.SYSTEM.list(dir).none { FileSystem.SYSTEM.metadata(it).size!! >= 520_000 }) {
                store.append(DiagnosticRecord("run", sequence++, 1000,
                    RuntimeDiagnosticEvent.ClientReady("p".repeat(3000))))
            }
            fs.reset()
            repeat(100) { store.append(DiagnosticRecord("run", sequence++, 1000,
                RuntimeDiagnosticEvent.ClientReady("pane"))) }
            assertTrue(fs.bytesWritten < 800_000, "100 small appends wrote ${fs.bytesWritten} bytes")
            assertTrue(fs.rewrites <= 1, "100 small appends rewrote ${fs.rewrites} files")
            assertTrue(fs.reads <= 1, "appends must not reparse the inactive file per event")
            assertTrue(RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot().contains("\"sequence\":${sequence - 1}"))
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun oneQueuedBatchCommitsWithOneSinkAndRecoversEveryRecord() {
        val dir = Files.createTempDirectory("diagnostics-batch").toOkioPath()
        try {
            val fs = CountingFileSystem()
            val store = RuntimeDiagnosticStore(dir, fs) { 1000L }
            store.append(DiagnosticRecord("run", 0, 1000, RuntimeDiagnosticEvent.ClientReady("start")))
            fs.reset()
            store.appendBatch((1L..256L).map { DiagnosticRecord("run", it, 1000, RuntimeDiagnosticEvent.ClientReady("pane")) })
            assertEquals(1, fs.appends)
            assertEquals(0, fs.rewrites)
            assertTrue(fs.bytesWritten < 40_000)
            assertEquals(257, RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot().lines().count { it.isNotEmpty() })
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun exportIncludesOnlyTheLatestTwoRuns() {
        val dir = Files.createTempDirectory("diagnostics-runs").toOkioPath()
        try {
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            listOf("old", "previous", "current").forEach { run ->
                store.append(DiagnosticRecord(run, 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            }
            val snapshot = store.snapshot()
            assertTrue(!snapshot.contains("\"runId\":\"old\""), snapshot)
            assertTrue(snapshot.contains("previous") && snapshot.contains("current"), snapshot)
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun legacySingleFooterGenerationLoadsAndRotatesWithoutLosingHistory() {
        val dir = Files.createTempDirectory("diagnostics-legacy").toOkioPath()
        try {
            FileSystem.SYSTEM.sink(dir / "diagnostics.0.ndjson").buffer().use {
                it.writeUtf8("{\"generation\":7}\n{\"runId\":\"legacy\",\"sequence\":1,\"epochMillis\":1000,\"event\":{\"type\":\"ClientReady\",\"paneId\":\"pane\"}}\n{\"checksum\":1896292895759850750}\n")
            }
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            assertTrue(store.snapshot().contains("legacy"))
            store.append(DiagnosticRecord("new", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane")))
            val reopened = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot()
            assertTrue(reopened.contains("legacy") && reopened.contains("new"))
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun interruptedAppendRecoversLastCheckpointAndCanContinue() {
        for (cutoff in listOf(0L, 1L, 32L, 150L)) {
            val dir = Files.createTempDirectory("diagnostics-append-fault").toOkioPath()
            try {
                var fail = false
                val fs = object : ForwardingFileSystem(FileSystem.SYSTEM) {
                    override fun appendingSink(file: Path, mustExist: Boolean): Sink =
                        object : ForwardingSink(super.appendingSink(file, mustExist)) {
                            var left = cutoff
                            override fun write(source: Buffer, byteCount: Long) {
                                if (!fail) { super.write(source, byteCount); return }
                                val count = minOf(left, byteCount)
                                if (count > 0) { super.write(source, count); left -= count }
                                if (count != byteCount) throw IOException("interrupted append")
                            }
                        }
                }
                val store = RuntimeDiagnosticStore(dir, fs) { 1000L }
                store.append(DiagnosticRecord("run", 1, 1000, RuntimeDiagnosticEvent.ClientReady("complete")))
                store.append(DiagnosticRecord("run", 2, 1000, RuntimeDiagnosticEvent.ClientReady("complete")))
                val before = store.snapshot()
                fail = true
                try { store.append(DiagnosticRecord("run", 3, 1000, RuntimeDiagnosticEvent.ClientReady("x".repeat(1000)))) }
                catch (_: IOException) { }
                assertEquals(before, RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot())
                fail = false
                store.append(DiagnosticRecord("run", 4, 1000, RuntimeDiagnosticEvent.ClientReady("recovered")))
                val recovered = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot()
                assertTrue(recovered.contains("recovered"))
                assertTrue(!recovered.contains("\"sequence\":3"))
            } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
        }
    }

    @Test fun fileCreationHookRunsOnlyForNewGenerationFiles() {
        val dir = Files.createTempDirectory("diagnostics-created").toOkioPath()
        try {
            val created = mutableListOf<Path>()
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM, { created.add(it) }) { 1000L }
            repeat(600) { store.append(DiagnosticRecord("run", it.toLong(), 1000,
                RuntimeDiagnosticEvent.ClientReady("p".repeat(3000)))) }
            assertEquals(setOf("diagnostics.0.ndjson", "diagnostics.1.ndjson"), created.map { it.name }.toSet())
            assertEquals(2, created.size)
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    private class CountingFileSystem : ForwardingFileSystem(FileSystem.SYSTEM) {
        var bytesWritten = 0L
        var rewrites = 0
        var reads = 0
        var appends = 0
        fun reset() { bytesWritten = 0; rewrites = 0; reads = 0; appends = 0 }
        override fun source(file: Path): okio.Source { reads++; return super.source(file) }
        override fun sink(file: Path, mustCreate: Boolean): Sink { rewrites++; return counted(super.sink(file, mustCreate)) }
        override fun appendingSink(file: Path, mustExist: Boolean): Sink { appends++; return counted(super.appendingSink(file, mustExist)) }
        private fun counted(sink: Sink): Sink = object : ForwardingSink(sink) {
            override fun write(source: Buffer, byteCount: Long) {
                bytesWritten += byteCount
                super.write(source, byteCount)
            }
        }
    }

    @Test fun persistedOwnershipAndTrackingDetachKeepTypedJoinFields() {
        val dir = Files.createTempDirectory("diagnostics-ownership").toOkioPath()
        try {
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            val events = listOf(
                RuntimeDiagnosticEvent.OwnershipObserved("host", "window", "scene", DiagnosticState.BACKGROUND,
                    DiagnosticState.BACKGROUND, DiagnosticState.STARTED, false, false, true),
                RuntimeDiagnosticEvent.PaneOwnershipObserved("pane", "view", "window", "scene", true),
                RuntimeDiagnosticEvent.ProbeResult("probe", "pane", "view", ProbeOutcome.ALIVE, false, 7),
                RuntimeDiagnosticEvent.HostTrackingDetached("host", "window", "scene"),
            )
            events.forEachIndexed { index, event -> store.append(DiagnosticRecord("run", index.toLong(), 1000, event)) }
            val records = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }.snapshot().lineSequence()
                .filter { it.isNotBlank() }.map { kotlinx.serialization.json.Json.parseToJsonElement(it) }.toList()
            fun event(index: Int) = (records[index] as kotlinx.serialization.json.JsonObject).getValue("event") as kotlinx.serialization.json.JsonObject
            fun field(index: Int, name: String) = (event(index).getValue(name) as kotlinx.serialization.json.JsonPrimitive).content
            assertEquals("PaneOwnershipObserved", field(1, "type"))
            assertEquals("window", field(1, "windowId"))
            assertEquals("scene", field(1, "sceneId"))
            assertEquals("true", field(1, "attached"))
            assertEquals(field(0, "windowId"), field(1, "windowId"))
            assertEquals(field(0, "sceneId"), field(1, "sceneId"))
            assertEquals(field(1, "paneId"), field(2, "paneId"))
            assertEquals(field(1, "webViewId"), field(2, "webViewId"))
            assertEquals("HostTrackingDetached", field(3, "type"))
            assertEquals("host", field(3, "hostId"))
            assertEquals("window", field(3, "windowId"))
            assertEquals("scene", field(3, "sceneId"))
            assertEquals(setOf("type", "hostId", "windowId", "sceneId"), event(3).keys)
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

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

    @Test fun multiBufferInterruptedWritesPreserveExactPreviousGeneration() {
        val fixture = Files.createTempDirectory("diagnostics-fixture").toOkioPath()
        try {
            val fs = FileSystem.SYSTEM
            val store = RuntimeDiagnosticStore(fixture, fs) { 1000L }
            repeat(65) { n -> store.append(DiagnosticRecord("ä".repeat(1900), n.toLong(), 1000, RuntimeDiagnosticEvent.ClientReady("pane"))) }
            val before = store.snapshot()
            val marker = DiagnosticRecord("new-marker", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane"))
            val nextNumber = fs.list(fixture).map { path ->
                fs.source(path).buffer().use { it.readUtf8LineStrict().substringAfter(':').substringBefore('}').toLong() }
            }.max() + 1
            val headerBytes = "{\"generation\":$nextNumber}\n".encodeToByteArray().size
            val bodyBytes = before.encodeToByteArray().size + (marker.encode() + "\n").encodeToByteArray().size
            val preFooter = headerBytes + bodyBytes
            assertTrue(preFooter > 250_000)
            for (cutoff in listOf(1, headerBytes - 1, 8192, 16384, preFooter - 1, preFooter, preFooter + 5)) {
                val dir = Files.createTempDirectory("diagnostics-interruption").toOkioPath()
                try {
                    fs.list(fixture).forEach { source -> fs.copy(source, dir / source.name) }
                    var failed = false
                    val failing = object : ForwardingFileSystem(fs) {
                        override fun sink(file: Path, mustCreate: Boolean): Sink = object : ForwardingSink(super.sink(file, mustCreate)) {
                            var remaining = cutoff.toLong()
                            override fun write(source: Buffer, byteCount: Long) {
                                val count = minOf(remaining, byteCount)
                                if (count > 0) { super.write(source, count); remaining -= count }
                                if (count < byteCount) { failed = true; throw IOException("injected write") }
                            }
                        }
                    }
                    try { RuntimeDiagnosticStore(dir, failing) { 1000L }.append(marker) } catch (_: IOException) { }
                    assertTrue(failed, "fault not triggered at $cutoff")
                    assertEquals(before, RuntimeDiagnosticStore(dir, fs) { 1000L }.snapshot(), "cutoff=$cutoff")
                } finally { fs.deleteRecursively(dir) }
            }
            val dir = Files.createTempDirectory("diagnostics-close").toOkioPath()
            try {
                fs.list(fixture).forEach { source -> fs.copy(source, dir / source.name) }
                var failed = false
                val failing = object : ForwardingFileSystem(fs) {
                    override fun sink(file: Path, mustCreate: Boolean): Sink = object : ForwardingSink(super.sink(file, mustCreate)) {
                        override fun close() { super.close(); failed = true; throw IOException("injected close") }
                    }
                }
                try { RuntimeDiagnosticStore(dir, failing) { 1000L }.append(marker) } catch (_: IOException) { }
                assertTrue(failed)
                assertTrue(RuntimeDiagnosticStore(dir, fs) { 1000L }.snapshot().contains("new-marker"))
            } finally { fs.deleteRecursively(dir) }
        } finally { FileSystem.SYSTEM.deleteRecursively(fixture) }
    }

    @Test fun interruptedSnapshotWritesKeepCompletedHistory() {
        for (cutoff in listOf(0L, 1L, 32L, 128L, 1024L)) {
            val dir = Files.createTempDirectory("diagnostics").toOkioPath()
            try {
                val base = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
                base.append(DiagnosticRecord("run", 1, 1000, RuntimeDiagnosticEvent.ClientReady("first")))
                base.append(DiagnosticRecord("run", 2, 1000, RuntimeDiagnosticEvent.ClientReady("second")))
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
            var now = 1000L
            val store = RuntimeDiagnosticStore(dir, fs) { now }
            repeat(180) { n ->
                if (n == 90) now = 1L
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

    @Test fun retentionRemovesExpiredBytesFromBothGenerationsOnReopen() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            var now = 1_000_000_000L
            val fs = FileSystem.SYSTEM
            val store = RuntimeDiagnosticStore(dir, fs) { now }
            store.append(DiagnosticRecord("expired-marker", 1, now, RuntimeDiagnosticEvent.ClientReady("pane")))
            store.append(DiagnosticRecord("fresh-marker", 1, now + 1, RuntimeDiagnosticEvent.ClientReady("pane")))
            now += 604800001L
            val reopened = RuntimeDiagnosticStore(dir, fs) { now }
            assertTrue(reopened.snapshot().contains("fresh-marker"))
            fs.list(dir).forEach { path ->
                assertTrue(!fs.source(path).buffer().use { it.readUtf8() }.contains("expired-marker"), "expired bytes in $path")
            }
            now -= 604800001L
            assertTrue(!RuntimeDiagnosticStore(dir, fs) { now }.snapshot().contains("expired-marker"))
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun interruptedPruningLeavesFreshHistoryRecoverable() {
        for (failDelete in listOf(false, true)) {
            val dir = Files.createTempDirectory("diagnostics").toOkioPath()
            try {
                var now = 1_000_000_000L
                val fs = FileSystem.SYSTEM
                val store = RuntimeDiagnosticStore(dir, fs) { now }
                store.append(DiagnosticRecord("expired-marker", 1, now, RuntimeDiagnosticEvent.ClientReady("pane")))
                store.append(DiagnosticRecord("fresh-marker", 1, now + 1, RuntimeDiagnosticEvent.ClientReady("pane")))
                now += 604800001L
                var failed = false
                val failing = object : ForwardingFileSystem(fs) {
                    override fun sink(file: Path, mustCreate: Boolean): Sink {
                        if (!failDelete) { failed = true; throw IOException("write failure") }
                        return super.sink(file, mustCreate)
                    }
                    override fun delete(path: Path, mustExist: Boolean) {
                        if (failDelete) { failed = true; throw IOException("delete failure") }
                        super.delete(path, mustExist)
                    }
                }
                try { RuntimeDiagnosticStore(dir, failing) { now } } catch (_: IOException) { }
                assertTrue(failed)
                val reopened = RuntimeDiagnosticStore(dir, fs) { now }
                assertTrue(reopened.snapshot().contains("fresh-marker"))
                fs.list(dir).forEach { path -> assertTrue(!fs.source(path).buffer().use { it.readUtf8() }.contains("expired-marker")) }
            } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
        }
    }

    @Test fun rejectedOversizeAppendDoesNotReadOrRewriteUntilStartupPrunes() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            var now = 1_000_000_000L
            val fs = FileSystem.SYSTEM
            val store = RuntimeDiagnosticStore(dir, fs) { now }
            store.append(DiagnosticRecord("expired-marker", 1, now, RuntimeDiagnosticEvent.ClientReady("pane")))
            store.append(DiagnosticRecord("fresh-marker", 1, now + 1, RuntimeDiagnosticEvent.ClientReady("pane")))
            now += 604800001L
            store.append(DiagnosticRecord("x".repeat(5000), 1, now, RuntimeDiagnosticEvent.ClientReady("pane")))
            assertEquals(1L, store.takeOmittedCount())
            assertTrue(!store.snapshot().contains("expired-marker"))
            val reopened = RuntimeDiagnosticStore(dir, fs) { now }
            fs.list(dir).forEach { path -> assertTrue(!fs.source(path).buffer().use { it.readUtf8() }.contains("expired-marker")) }
            assertTrue(reopened.snapshot().contains("fresh-marker"))
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
    }

    @Test fun exactUtf8RecordBoundaryIsEnforced() {
        val dir = Files.createTempDirectory("diagnostics").toOkioPath()
        try {
            val store = RuntimeDiagnosticStore(dir, FileSystem.SYSTEM) { 1000L }
            val blank = DiagnosticRecord("", 1, 1000, RuntimeDiagnosticEvent.ClientReady("pane"))
            val overhead = (blank.encode() + "\n").encodeToByteArray().size
            val ascii = blank.copy(runId = "a".repeat(4096 - overhead))
            val multi = blank.copy(runId = "ä".repeat((4096 - overhead) / 2) + "a".repeat((4096 - overhead) % 2))
            val tooLarge = multi.copy(runId = multi.runId + "a")
            assertEquals(4096, (ascii.encode() + "\n").encodeToByteArray().size)
            assertEquals(4096, (multi.encode() + "\n").encodeToByteArray().size)
            assertEquals(4097, (tooLarge.encode() + "\n").encodeToByteArray().size)
            assertTrue(tooLarge.encode().length < 4096)
            store.append(ascii); store.append(multi); store.append(tooLarge)
            assertEquals(1L, store.takeOmittedCount())
            assertEquals(2, store.snapshot().lines().count { it.isNotEmpty() })
        } finally { FileSystem.SYSTEM.deleteRecursively(dir) }
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
