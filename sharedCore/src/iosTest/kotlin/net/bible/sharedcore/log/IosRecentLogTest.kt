package net.bible.sharedcore.log

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosRecentLogTest {
    @Test fun platformLoggingFeedsSnapshotInOrder() {
        val marker = platform.Foundation.NSUUID().UUIDString
        Log.i("ReportTest", "$marker first")
        Log.w("ReportTest", "$marker second")
        val snapshot = iosRecentLogSnapshot()
        assertTrue(snapshot.indexOf("$marker first") < snapshot.indexOf("$marker second"))
        assertTrue("$marker first" in snapshot)
    }
    private val omission = "[oversized log entry omitted]\n"

    private fun assertSnapshot(log: RecentIosLog, limit: Int, expected: String) {
        val snapshot = log.snapshot()
        assertEquals(expected, snapshot)
        assertTrue(snapshot.length <= limit)
    }

    @Test fun emptyAndExactFit() {
        val log = RecentIosLog(4)
        assertSnapshot(log, 4, "")
        log.append("abc")
        assertSnapshot(log, 4, "abc\n")
    }

    @Test fun overflowEvictsWholeEntriesInOrder() {
        val log = RecentIosLog(10)
        log.append("first")
        log.append("second")
        assertSnapshot(log, 10, "second\n")
        log.append("a")
        assertSnapshot(log, 10, "second\na\n")
        log.append("b")
        assertSnapshot(log, 10, "a\nb\n")
        log.append("c")
        assertSnapshot(log, 10, "a\nb\nc\n")
    }

    @Test fun multilineEntryIsRetainedAndEvictedWhole() {
        val log = RecentIosLog(12)
        log.append("a\nb")
        log.append("last")
        assertSnapshot(log, 12, "a\nb\nlast\n")
        log.append("next")
        assertSnapshot(log, 12, "last\nnext\n")
    }

    @Test fun oversizedEntryPreservesHistoryAndRecovers() {
        val log = RecentIosLog(50)
        log.append("old")
        log.append("x\n" + "z".repeat(50))
        assertSnapshot(log, 50, "old\n" + omission)
        log.append("new")
        assertSnapshot(log, 50, "old\n" + omission + "new\n")
    }

    @Test fun markerEvictsOnlyOldestEntriesNeededToFit() {
        val log = RecentIosLog(35)
        log.append("old")
        log.append("keep")
        log.append("x".repeat(35))
        assertSnapshot(log, 35, "keep\n" + omission)
    }

    @Test fun tinyLimitsPreserveHistoryWithoutMarker() {
        for (limit in listOf(0, 1, 4, omission.length - 1)) {
            val log = RecentIosLog(limit)
            if (limit >= 4) log.append("abc")
            val expected = if (limit >= 4) "abc\n" else ""
            log.append("x".repeat(limit))
            assertSnapshot(log, limit, expected)
        }
    }

    @Test fun repeatedOversizedEntriesAndExactMarkerFit() {
        val limit = omission.length * 2
        val log = RecentIosLog(limit)
        log.append("x".repeat(limit))
        log.append("y".repeat(limit))
        assertSnapshot(log, limit, omission + omission)
        log.append("z".repeat(limit))
        assertSnapshot(log, limit, omission + omission)

        val exact = RecentIosLog(omission.length)
        exact.append("x".repeat(omission.length))
        assertSnapshot(exact, omission.length, omission)
    }

    @Test fun supplementaryCharactersAreNeverSplitByEviction() {
        val log = RecentIosLog(6)
        log.append("A😀")
        assertSnapshot(log, 6, "A😀\n")
        log.append("B")
        assertSnapshot(log, 6, "A😀\nB\n")
        log.append("C")
        assertSnapshot(log, 6, "B\nC\n")
    }

    @Test fun concurrentSnapshotsContainOnlyCompleteSubmittedEntries() = runBlocking {
        val limit = 128
        val log = RecentIosLog(limit)
        val entries = (0 until 400).map { "entry-${it.toString().padStart(3, '0')}" }
        val submitted = entries.toSet()
        val start = CompletableDeferred<Unit>()
        val workers = (0 until 4).map { worker ->
            async(Dispatchers.Default) {
                start.await()
                repeat(100) { index ->
                    log.append(entries[worker * 100 + index])
                    val snapshot = log.snapshot()
                    assertTrue(snapshot.length <= limit)
                    assertTrue(snapshot.isEmpty() || snapshot.endsWith("\n"))
                    val lines = snapshot.split('\n').dropLast(1)
                    assertTrue(lines.all { it in submitted }, snapshot)
                    assertEquals(lines.size, lines.toSet().size, snapshot)
                }
            }
        }
        start.complete(Unit)
        workers.awaitAll()
        Unit
    }
    @Test fun concurrentAppendsRetainEveryEntryWhenCapacityIsSufficient() = runBlocking {
        val entries = (0 until 400).map { "entry-${it.toString().padStart(3, '0')}" }
        val limit = entries.sumOf { it.length + 1 }
        val log = RecentIosLog(limit)
        val start = CompletableDeferred<Unit>()
        val workers = (0 until 4).map { worker ->
            async(Dispatchers.Default) {
                start.await()
                repeat(100) { index -> log.append(entries[worker * 100 + index]) }
            }
        }
        start.complete(Unit)
        workers.awaitAll()
        val snapshot = log.snapshot()
        assertEquals(limit, snapshot.length)
        assertTrue(snapshot.endsWith("\n"))
        val lines = snapshot.split('\n').dropLast(1)
        assertEquals(entries.size, lines.size)
        assertEquals(entries.toSet(), lines.toSet())
        Unit
    }

    private val stamp = Regex("""(?m)^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \[[^\]]+\] I/FmtTest: """)

    @Test fun snapshotLinesCarryTimestampAndThread() {
        val marker = platform.Foundation.NSUUID().UUIDString
        Log.i("FmtTest", "$marker line")
        val line = iosRecentLogSnapshot().lines().single { marker in it }
        assertTrue(stamp.containsMatchIn(line), line)
        assertTrue("[main]" in line, line)
    }

    @Test fun containerPathIsShortenedInTheSnapshot() {
        registerIosLogPathAliases()
        val marker = platform.Foundation.NSUUID().UUIDString
        val home = platform.Foundation.NSHomeDirectory()
        Log.i("FmtTest", "$marker $home/Documents/x")
        val line = iosRecentLogSnapshot().lines().single { marker in it }
        assertTrue("$marker <container>/Documents/x" in line, line)
    }
}
