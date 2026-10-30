package net.bible.sharedcore.log

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlatformLogRingTest {
    @Test
    fun ringBufferBounded() {
        val ring = RecentJvmLog(12)
        ring.append("old entry")
        ring.append("new entry")
        assertEquals("y\nnew entry\n", ring.snapshot())
        ring.append("x".repeat(30))
        assertTrue(ring.snapshot().length <= 12)
        assertEquals("x".repeat(11) + "\n", ring.snapshot())
    }
}
