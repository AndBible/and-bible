package net.bible.sharedcore.log

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformLogFormatTest {
    private val stamp = Regex("""(?m)^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \[[^\]]+\] I/FmtTest: """)

    @Test fun recentBufferLinesCarryTimestampAndThreadOfTheCaller() {
        val marker = java.util.UUID.randomUUID().toString()
        val t = Thread({ Log.i("FmtTest", "$marker worker") }, "worker-x")
        t.start(); t.join()
        val line = recentLogSnapshot().lines().single { marker in it }
        assertTrue(stamp.containsMatchIn(line), line)
        assertTrue("[worker-x" in line, line)
    }

    @Test fun homeDirectoryIsShortenedInTheBuffer() {
        val home = System.getProperty("user.home")
        val marker = java.util.UUID.randomUUID().toString()
        Log.i("FmtTest", "$marker $home/Documents/x")
        val line = recentLogSnapshot().lines().single { marker in it }
        assertTrue("$marker ~/Documents/x" in line, line)
    }

    @Test fun fileAndBufferShareTheFormat() {
        val dir = java.nio.file.Files.createTempDirectory("fmt").resolve("logs")
        configureDesktopLogFile(dir)
        try {
            Log.i("FmtTest", "to-file")
            val fileLine = java.nio.file.Files.readString(dir.resolve("andbible.log")).lines().first { "to-file" in it }
            assertTrue(stamp.containsMatchIn(fileLine), fileLine)
            val bufferLine = recentLogSnapshot().lines().last { "to-file" in it }
            assertTrue(fileLine == bufferLine, "$fileLine != $bufferLine")
        } finally { DesktopLogFile.close() }
    }
}
