package net.bible.sharedcore.log

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LogTest {
    private val seen = mutableListOf<Triple<LogLevel, String, Throwable?>>()

    @AfterTest fun reset() { Log.sinkOverride = null }

    @Test fun routesEveryLevelWithTagMessageAndThrowable() {
        Log.sinkOverride = { level, tag, msg, tr -> seen += Triple(level, "$tag:$msg", tr) }
        val boom = IllegalStateException("x")
        Log.d("T", "d"); Log.i("T", "i"); Log.w("T", "w"); Log.e("T", "e", boom); Log.v("T", "v")
        Log.w("T", boom)
        assertEquals(listOf(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR, LogLevel.VERBOSE, LogLevel.WARN), seen.map { it.first })
        assertEquals("T:e", seen[3].second)
        assertSame(boom, seen[3].third)
        assertSame(boom, seen[5].third)
    }

    @Test fun stackTraceStringContainsTheMessageAndIsEmptyForNull() {
        assertTrue(Log.getStackTraceString(IllegalArgumentException("needle")).contains("needle"))
        assertEquals("", Log.getStackTraceString(null))
    }

    @Test fun aThrowingSinkNeverEscapes() {
        Log.sinkOverride = { _, _, _, _ -> error("sink down") }
        Log.e("T", "still fine")  // must not throw
    }
}
