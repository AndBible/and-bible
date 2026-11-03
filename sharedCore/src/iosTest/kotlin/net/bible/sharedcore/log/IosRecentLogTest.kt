package net.bible.sharedcore.log

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
    @Test fun boundsSnapshotAndPreservesOrder() {
        val log = RecentIosLog(10)
        log.append("first")
        log.append("second")
        assertEquals("st\nsecond\n", log.snapshot())
        log.append("0123456789012345")
        assertTrue(log.snapshot().length <= 10)
        assertTrue(log.snapshot().endsWith("\n"))
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
